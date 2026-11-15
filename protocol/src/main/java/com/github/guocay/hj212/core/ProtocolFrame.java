package com.github.guocay.hj212.core;

import com.github.guocay.hj212.exception.ProtocolFormatException;
import java.io.*;
import java.util.Objects;

/** 按数据段长度读取原始字节帧，可用于含任意二进制字节的 SM4 报文。 */
public final class ProtocolFrame {
    private ProtocolFrame() { }

    /** 正常 EOF 返回 null；残缺帧抛 EOFException；不关闭输入流，也不读走下一帧。 */
    public static byte[] read(InputStream input) throws IOException, ProtocolFormatException {
        Objects.requireNonNull(input,"input");
        int first=input.read();
        if(first<0) return null;
        byte[] head=new byte[6];head[0]=(byte)first;
        readFully(input,head,1,5);
        if(head[0]!='#' || head[1]!='#') throw new ProtocolFormatException("无效的包头");
        int length=0;
        for(int i=2;i<6;i++) {
            if(head[i]<'0' || head[i]>'9') throw new ProtocolFormatException("无效的数据段长度");
            length=length*10+head[i]-'0';
        }
        if(length>1024) throw new ProtocolFormatException("数据段长度不能超过 1024 字节");
        byte[] result=new byte[length+12];
        System.arraycopy(head,0,result,0,head.length);
        readFully(input,result,6,length+6);
        if(result[result.length-2]!='\r' || result[result.length-1]!='\n') throw new ProtocolFormatException("无效的包尾");
        return result;
    }
    private static void readFully(InputStream input,byte[] buffer,int offset,int length) throws IOException {
        int end=offset+length;
        while(offset<end) {
            int n=input.read(buffer,offset,end-offset);
            if(n<0) throw new EOFException("HJ212 报文尚未完整接收");
            if(n==0) {int b=input.read();if(b<0)throw new EOFException("HJ212 报文尚未完整接收");buffer[offset++]=(byte)b;}
            else offset+=n;
        }
    }
}
