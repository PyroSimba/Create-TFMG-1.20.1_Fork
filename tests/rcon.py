"""Minimal RCON client, stdlib only (Source RCON protocol as used by Minecraft)."""
import socket
import struct

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


class RconError(Exception):
    pass


class Rcon:
    def __init__(self, host="127.0.0.1", port=25575, password="", timeout=10):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self._id = 0
        self._send(SERVERDATA_AUTH, password)
        req_id, _, _ = self._recv()
        if req_id == -1:
            raise RconError("RCON auth failed")

    def _send(self, ptype, body):
        self._id += 1
        payload = struct.pack("<ii", self._id, ptype) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        return self._id

    def _recv(self):
        def read_exact(n):
            buf = b""
            while len(buf) < n:
                chunk = self.sock.recv(n - len(buf))
                if not chunk:
                    raise RconError("connection closed")
                buf += chunk
            return buf

        (length,) = struct.unpack("<i", read_exact(4))
        data = read_exact(length)
        req_id, ptype = struct.unpack("<ii", data[:8])
        body = data[8:-2].decode("utf-8", errors="replace")
        return req_id, ptype, body

    def cmd(self, command):
        self._send(SERVERDATA_EXECCOMMAND, command)
        _, _, body = self._recv()
        return body

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


if __name__ == "__main__":
    import sys
    r = Rcon(password="tfmgtest")
    print(r.cmd(" ".join(sys.argv[1:]) or "list"))
    r.close()
