"""Local RCON smoke helper. Read test credentials from MC_RCON_PASSWORD; never prints them."""
import os
import socket
import struct
import sys


def exact(connection, size):
    data = b""
    while len(data) < size:
        chunk = connection.recv(size - len(data))
        if not chunk:
            raise ConnectionError("RCON disconnected")
        data += chunk
    return data


def receive(connection):
    size = struct.unpack("<i", exact(connection, 4))[0]
    if not 10 <= size <= 4 * 1024 * 1024:
        raise ValueError("Invalid RCON packet size")
    body = exact(connection, size)
    request, kind = struct.unpack("<ii", body[:8])
    return request, kind, body[8:-2].decode("utf-8")


def send(connection, request, kind, text):
    body = struct.pack("<ii", request, kind) + text.encode() + b"\0\0"
    connection.sendall(struct.pack("<i", len(body)) + body)


if __name__ == "__main__":
    with socket.create_connection(("127.0.0.1", int(os.environ.get("MC_RCON_PORT", "25575"))), timeout=10) as conn:
        send(conn, 1, 3, os.environ["MC_RCON_PASSWORD"])
        while True:
            request, kind, _ = receive(conn)
            if request == -1:
                raise PermissionError("RCON authentication rejected")
            if kind == 2:
                break
        send(conn, 2, 2, " ".join(sys.argv[1:]))
        request, kind, response = receive(conn)
        if request != 2:
            raise RuntimeError("Unexpected RCON response")
        print(response)
