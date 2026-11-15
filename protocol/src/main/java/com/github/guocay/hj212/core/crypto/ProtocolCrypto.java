package com.github.guocay.hj212.core.crypto;

import com.github.guocay.hj212.core.ProtocolParser;
import com.github.guocay.hj212.exception.ProtocolFormatException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** HJ 212—2025 附录 A.2 的字节报文转换；不保存密钥，不处理密钥生命周期。 */
public final class ProtocolCrypto {
    private ProtocolCrypto() { }

    /** 输入必须是已经组包且 CRC 正确的明文报文。返回新的二进制报文。 */
    public static byte[] encrypt(byte[] packet, byte[] key) throws ProtocolFormatException {
        int start = cpStart(packet);
        verifyCrc(packet);
        return transform(packet, key, start, true);
    }

    /** 解密后校验明文 CRC；错误密钥或损坏报文统一失败，不返回不可信明文。 */
    public static byte[] decrypt(byte[] packet, byte[] key) throws ProtocolFormatException {
        int start = cpStart(packet);
        byte[] result = transform(packet, key, start, false);
        try { verifyCrc(result); }
        catch (ProtocolFormatException ex) {
            Arrays.fill(result, (byte) 0);
            throw new ProtocolFormatException("解密后的报文 CRC 校验失败");
        }
        return result;
    }

    private static byte[] transform(byte[] packet, byte[] key, int start, boolean encrypt) {
        if (key == null || key.length != 16) throw new IllegalArgumentException("SM4 密钥必须是 16 字节");
        byte[] copy = key.clone();
        byte[] result = packet.clone();
        try {
            SM4Engine engine = new SM4Engine();
            engine.init(encrypt, new KeyParameter(copy));
            int end = packet.length - 8; // 排除闭合 &&、CRC 和 CRLF。
            int encryptedLength = ((end - start) / 16) * 16;
            for (int offset = start; offset < start + encryptedLength; offset += 16)
                engine.processBlock(packet, offset, result, offset);
            // 余下不足 16 字节的部分按标准保留明文，不补零、不补 PKCS#7。
            return result;
        } finally { Arrays.fill(copy, (byte) 0); }
    }

    private static int cpStart(byte[] packet) throws ProtocolFormatException {
        if (packet == null || packet.length < 12 || packet[0] != '#' || packet[1] != '#') fail();
        int len = 0;
        for (int i = 2; i < 6; i++) {
            if (packet[i] < '0' || packet[i] > '9') fail();
            len = len * 10 + packet[i] - '0';
        }
        if (len > 1024 || packet.length != len + 12 || packet[packet.length - 2] != '\r'
                || packet[packet.length - 1] != '\n') fail();
        int end = packet.length - 8;
        if (end < 6 || packet[end] != '&' || packet[end + 1] != '&') fail();
        byte[] marker = ";CP=&&".getBytes(StandardCharsets.US_ASCII);
        for (int i = 6; i + marker.length <= end; i++) {
            boolean match = true;
            for (int j = 0; j < marker.length; j++) if (packet[i+j] != marker[j]) { match = false; break; }
            if (match) return i + marker.length;
        }
        fail();
        return -1;
    }

    private static void verifyCrc(byte[] packet) throws ProtocolFormatException {
        int expected = 0;
        for (int i = packet.length - 6; i < packet.length - 2; i++) {
            int digit = Character.digit((char) (packet[i] & 255), 16);
            if (digit < 0) fail();
            expected = expected * 16 + digit;
        }
        int actual = ProtocolParser.crc16Checkout(packet, 6, packet.length - 12);
        if (expected != actual) throw new ProtocolFormatException("报文 CRC 校验失败");
    }

    private static void fail() throws ProtocolFormatException {
        throw new ProtocolFormatException("报文长度、包头、CP 边界或包尾无效");
    }
}
