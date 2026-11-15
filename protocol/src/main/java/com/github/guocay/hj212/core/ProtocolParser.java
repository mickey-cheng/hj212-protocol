package com.github.guocay.hj212.core;

import com.github.guocay.hj212.exception.ProtocolFormatException;
import com.github.guocay.hj212.model.verify.PacketElement;
import com.github.guocay.hj212.segment.config.Configurator;
import com.github.guocay.hj212.segment.config.Configured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

import static com.github.guocay.hj212.core.feature.ParserFeature.FOOTER_CONSTANT;
import static com.github.guocay.hj212.core.feature.ParserFeature.HEADER_CONSTANT;

/**
 * T212通信包解析器
 * @author aCay
 */
public class ProtocolParser implements Configured<ProtocolParser>, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolParser.class);

    public static char[] HEADER = new char[]{ '#','#' };
    public static char[] FOOTER = new char[]{ '\r', '\n' };

    protected Reader reader;
    //
    private int parserFeature;

    //TEMP
    private int count;

    public ProtocolParser(Reader reader){
        this.reader = reader;
    }

    /**
     * 设置解析特性
     * @param parserFeature 解析特性
     */
    private int readFully(char[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int n = reader.read(target, offset, target.length - offset);
            if (n < 0) break;
            if (n == 0) {
                int c = reader.read();
                if (c < 0) break;
                target[offset++] = (char) c;
            } else offset += n;
        }
        return offset;
    }

    public void setParserFeature(int parserFeature) {
        this.parserFeature = parserFeature;
    }


    /**
     * 读取 包头
     * @see PacketElement#HEADER
     * @return chars
     * @throws ProtocolFormatException
     * @throws IOException
     */
    public char[] readHeader() throws ProtocolFormatException, IOException {
        char[] header = new char[2];
        count = readFully(header);
        VerifyUtil.verifyLen(count, 2, PacketElement.HEADER);
        if(HEADER_CONSTANT.enabledIn(parserFeature)){
            VerifyUtil.verifyChar(header, HEADER, PacketElement.HEADER);
        }
        return header;
    }

    /**
     * 读取 数据段长度
     * @see PacketElement#DATA_LEN
     * @return chars
     * @throws ProtocolFormatException
     * @throws IOException
     */
    public char[] readDataLen() throws ProtocolFormatException, IOException {
        char[] len = new char[4];
        count = readFully(len);
        VerifyUtil.verifyLen(count,len.length, PacketElement.DATA_LEN);
        if (!new String(len).matches("[0-9]{4}")) throw new IOException("数据段长度必须是四位十进制数字");
        return len;
    }

    /**
     * 读取 4字节Integer
     * @param radix 进制
     * @return Integer
     * @throws IOException
     */
    public int readInt32(int radix) throws IOException {
        char[] intChars = new char[4];
        count = readFully(intChars);
        if(count != 4){
            return -1;
        }
        String digits = new String(intChars);
        String pattern = radix == 10 ? "[0-9]{4}" : radix == 16 ? "[0-9A-Fa-f]{4}" : null;
        if (pattern != null && !digits.matches(pattern)) throw new IOException("长度或 CRC 字段格式无效");
        return Integer.parseInt(digits,radix);
    }

    /**
     * 读取 数据段
     * @see PacketElement#DATA
     * @return chars
     * @throws ProtocolFormatException
     * @throws IOException
     */
    public char[] readData(int segmentLen) throws ProtocolFormatException, IOException {
        StringBuilder segment = new StringBuilder();
        int bytes = 0;
        while (bytes < segmentLen) {
            int c = reader.read();
            if (c < 0) break;
            segment.append((char) c);
            if (Character.isHighSurrogate((char) c)) {
                int low = reader.read();
                if (low < 0 || !Character.isLowSurrogate((char) low))
                    throw new IOException("无效的 UTF-8 字符");
                segment.append((char) low);
                bytes += 4;
            } else bytes += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
        }
        VerifyUtil.verifyLen(bytes,segmentLen, PacketElement.DATA);
        return segment.toString().toCharArray();
    }

    /**
     * 读取 DATA_CRC 校验
     * @see PacketElement#DATA_CRC
     * @return header chars
     * @throws ProtocolFormatException
     * @throws IOException
     */
    public char[] readCrc() throws ProtocolFormatException, IOException {
        char[] crc = new char[4];
        count = readFully(crc);
        VerifyUtil.verifyLen(count,crc.length, PacketElement.DATA_CRC);
        if (!new String(crc).matches("[0-9A-Fa-f]{4}")) throw new IOException("CRC 必须是四位十六进制数字");
        return crc;
    }

    /**
     * 读取 数据段长度
     * @see PacketElement#DATA_LEN
     * @return chars
     * @throws ProtocolFormatException
     * @throws IOException
     */
    public char[] readFooter() throws ProtocolFormatException, IOException {
        char[] footer = new char[2];
        count = readFully(footer);
        VerifyUtil.verifyLen(count, 2, PacketElement.FOOTER);
        if(FOOTER_CONSTANT.enabledIn(parserFeature)){
            VerifyUtil.verifyChar(footer, FOOTER, PacketElement.FOOTER);
        }
        return footer;
    }

    /**
     * 读取 包尾
     * @see PacketElement#FOOTER
     * @return chars
     * @throws ProtocolFormatException
     * @throws IOException
     */
    public char[] readDataAndCrc(int dataLen) throws IOException, ProtocolFormatException {
        reader.mark(dataLen + 4);
        char[] data = readData(dataLen);

        int crc = readInt32(16);
        if(crc != -1 &&
                crc16Checkout(data,data.length) == crc){
            return data;
        }
        reader.reset();
        return null;
    }


    @Override
    public void configured(Configurator<ProtocolParser> configurator) {
        configurator.config(this);
    }

    @Override
    public void close(){
        try {
            reader.close();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * CRC校验
     * @param msg 消息
     * @param length 长度
     * @return DATA_CRC 校验码
     */
    public static int crc16Checkout(char[] msg, int length) {
        byte[] encoded = new String(msg, 0, length).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return crc16Checkout(encoded, 0, encoded.length);
    }

    /** 对原始传输字节计算 CRC，适用于二进制加密报文解密后的校验。 */
    public static int crc16Checkout(byte[] encoded, int offset, int length) {
        if (offset < 0 || length < 0 || offset > encoded.length - length)
            throw new IndexOutOfBoundsException("CRC 字节范围无效");
        int i,j,crc_reg,check;
        crc_reg = 0xFFFF;
        for(i=offset; i<offset+length; i++) {
            crc_reg = (crc_reg>>8) ^ (encoded[i] & 0xFF);
            for(j=0;j<8;j++) {
                check = crc_reg & 0x0001;
                crc_reg >>= 1;
                if (check == 0x0001) {
                    crc_reg ^= 0xA001;
                }
            }
        }
        return crc_reg;
    }

}
