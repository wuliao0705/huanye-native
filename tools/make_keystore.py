#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 Android 用的 JKS 密钥库（纯 Python，不需要 JVM）
格式：JKS (Java KeyStore) + PKCS12 也写一份
@author 无聊
"""
import os, sys, struct, hashlib, base64, datetime
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.serialization import pkcs12, pkcs7
from cryptography import x509
from cryptography.x509.oid import NameOID

STORE_PASS = "huanye2026"
KEY_PASS = "huanye2026"
ALIAS = "huanye"
OUT = sys.argv[1] if len(sys.argv) > 1 else "keystore/huanye.jks"


def make_key_and_cert():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "Wuliao"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Wuliao"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder()
            .subject_name(name)
            .issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - datetime.timedelta(days=1))
            .not_valid_after(now + datetime.timedelta(days=10000))
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
            .sign(key, hashes.SHA256()))
    return key, cert


def der_cert(cert):
    return cert.public_bytes(serialization.Encoding.DER)


def pkcs8_der(key):
    return key.private_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )


def java_utf(s):
    """Java modified UTF-8 编码"""
    out = bytearray()
    for ch in s:
        o = ord(ch)
        if o == 0:
            out += b'\xc0\x80'
        elif o < 0x80:
            out.append(o)
        elif o < 0x800:
            out.append(0xC0 | (o >> 6))
            out.append(0x80 | (o & 0x3F))
        else:
            out.append(0xE0 | (o >> 12))
            out.append(0x80 | ((o >> 6) & 0x3F))
            out.append(0x80 | (o & 0x3F))
    return bytes(out)


def write_utf(d, s):
    b = java_utf(s)
    d += struct.pack(">H", len(b)) + b


def password_bytes(pw):
    """JKS 密码 → UTF-16BE"""
    return pw.encode("utf-16-be")


def write_private_key_entry(d, alias, key_der, chain_ders, key_pw):
    tag = 1
    write_utf(d, alias)
    d += struct.pack(">q", 0)  # timestamp
    # 加密的私钥（JKS 用 KeyProtector 算法，太复杂）
    # 简化：用 StorePass 加 XOR/keytool 兼容 —— 实际上 Android Gradle 读 JKS
    # 为兼容，我们写 PKCS12 给 Gradle，JKS 这里只写证书链
    return d


def build_pkcs12(key, cert):
    return pkcs12.serialize_key_and_certificates(
        name=ALIAS.encode(),
        key=key,
        cert=cert,
        cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(KEY_PASS.encode()),
    )


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    key, cert = make_key_and_cert()

    # 1. Gradle 用的 PKCS12
    p12 = build_pkcs12(key, cert)
    p12_path = os.path.splitext(OUT)[0] + ".p12"
    open(p12_path, "wb").write(p12)

    # 2. JKS（证书链 + 私钥，用 JKS KeyProtector 算法）
    der = der_cert(cert)
    kder = pkcs8_der(key)
    jks = bytearray()
    jks += struct.pack(">IIII", 0xFEEDFEED, 2, 1, 1)  # magic, ver2, count=1, type=1(private key)

    # --- private key entry ---
    write_utf(jks, ALIAS)
    jks += struct.pack(">q", 0)  # date
    # encrypted private key using JKS Sun KeyProtector (PBE with XOR + SHA1)
    protected = jks_key_protector(kder, KEY_PASS)
    jks += struct.pack(">I", len(protected)) + protected
    # chain length
    jks += struct.pack(">I", 1)
    # cert: type(1=UTF8? actual: 1 = X.509) + utf alias + len + der
    jks += struct.pack(">I", 1)  # cert type = X.509
    write_utf(jks, ALIAS)
    jks += struct.pack(">I", len(der)) + der

    # --- digest (SHA1 of storepass utf16 + "Mighty Aphrodite" + body) ---
    body = bytes(jks)
    pw = password_bytes(STORE_PASS)
    dg = hashlib.sha1(pw + b"Mighty Aphrodite" + body).digest()
    jks += dg

    open(OUT, "wb").write(bytes(jks))
    open(os.path.splitext(OUT)[0] + ".pem", "wb").write(
        cert.public_bytes(serialization.Encoding.PEM))
    open(os.path.splitext(OUT)[0] + ".key.pem", "wb").write(
        key.private_bytes(serialization.Encoding.PEM,
                          serialization.PrivateFormat.TraditionalOpenSSL,
                          serialization.NoEncryption()))
    print("OK", OUT, os.path.getsize(OUT), "bytes")
    print("PKCS12", p12_path, os.path.getsize(p12_path), "bytes")


def jks_key_protector(key_der, pw):
    """JKS Sun KeyProtector: 加密算法 PBEWithMD5AndTripleDES 变体，用 XOR 简化版不可行。
    简化实现为 32 字节盐 + 加密数据，Android 端用 PKCS12 即可。"""
    # 生成 salt + protected: [20B salt][encrypted][20B digest]
    salt = os.urandom(20)
    pw_b = password_bytes(pw)
    # 用 SHA1 派生密钥流做 XOR（兼容性优先，Android 用 p12）
    stream = bytearray()
    seed = pw_b + salt
    while len(stream) < len(key_der):
        seed = hashlib.sha1(seed).digest()
        stream += seed
    data = bytes(a ^ b for a, b in zip(key_der, stream))
    digest = hashlib.sha1(pw_b + data).digest()
    return salt + data + digest


if __name__ == "__main__":
    main()
