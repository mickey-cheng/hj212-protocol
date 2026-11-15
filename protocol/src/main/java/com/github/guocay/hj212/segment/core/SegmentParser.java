package com.github.guocay.hj212.segment.core;

import com.github.guocay.hj212.segment.config.Configurator;
import com.github.guocay.hj212.segment.config.Configured;
import com.github.guocay.hj212.stream.ReaderStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Stack;

import static com.github.guocay.hj212.segment.core.SegmentToken.END_ENTRY;
import static com.github.guocay.hj212.segment.core.SegmentToken.END_KEY;
import static com.github.guocay.hj212.segment.core.SegmentToken.END_OBJECT_VALUE;
import static com.github.guocay.hj212.segment.core.SegmentToken.END_PART_KEY;
import static com.github.guocay.hj212.segment.core.SegmentToken.END_SUB_ENTRY;
import static com.github.guocay.hj212.segment.core.SegmentToken.NOT_AVAILABLE;
import static com.github.guocay.hj212.segment.core.SegmentToken.NULL_VALUE;
import static com.github.guocay.hj212.segment.core.SegmentToken.START_OBJECT_VALUE;
import static com.github.guocay.hj212.segment.core.feature.SegmentParserFeature.ALLOW_ISOLATED_KEY;
import static com.github.guocay.hj212.segment.core.feature.SegmentParserFeature.ALLOW_KEY_NOT_CLOSED;
import static com.github.guocay.hj212.segment.core.feature.SegmentParserFeature.IGNORE_INVAILD_SYMBOL;

/**
 * @author aCay
 */
@SuppressWarnings("incomplete-switch")
public class SegmentParser implements Closeable, Configured<SegmentParser> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentParser.class);

    protected PushbackReader reader;
    private int parserFeature;
    private SegmentToken currentToken;
    private Stack<String> path;
    private String lastKey;

    public SegmentParser(Reader reader){
        this.reader = new PushbackReader(reader,3);
        this.path = new Stack<>();
    }

    /**
     * 读取KEY
     * @return KEY
     * @throws IOException
     */
    public String readKey() throws IOException {
        return readPathKey(false);
    }

    /**
     * 读取一部分KEY
     * @return 部分KEY
     * @throws IOException
     */
    public String readPathKey() throws IOException {
        return readPathKey(true);
    }

    /**
     * 读取KEY
     * @param supportSubKey true：遇到‘-’结束
     * @return KEY
     * @throws IOException
     */
	private String readPathKey(boolean supportSubKey) throws IOException {
        //当前Token
        switch (currentToken){
            case END_KEY:
                throw new IOException("Cant read key after END_KEY token!");
            case END_OBJECT_VALUE:
                //“&&”字符串后面跟着“,”“;”认为是相同的，需要读出来，
                // 避免被认为是当前是孤立KEY
                ReaderStream.of(reader)
                        .next()
                            .when(END_SUB_ENTRY::isSame)
                            .then(() -> currentToken = END_SUB_ENTRY)
                            .when(END_ENTRY::isSame)
                            .then(() -> currentToken = END_ENTRY)
                            .done()
                        .match();
                break;
        }

        //一次性读取多少位,必须包括字符串中的键值对
        CharBuffer buffer = CharBuffer.allocate(1024);

        //之后的Token
        int len = ReaderStream.of(reader)
                .next()
                    .when(NOT_AVAILABLE::isSame)
                    .then(() -> currentToken = NOT_AVAILABLE)

                    //perfect
                    .when(c -> supportSubKey && END_PART_KEY.isSame(c))
                    .then(() -> currentToken = END_PART_KEY)
                    .when(END_KEY::isSame)
                    .then()
                        .next(2)
                            .when(START_OBJECT_VALUE::isSame)
                            .then(() -> currentToken = START_OBJECT_VALUE)
                            .done()
                        .back()
                    .then(() -> currentToken = END_KEY)

                    .when(END_SUB_ENTRY::isSame)
                    .then(() -> {
                        //Missing '=' core value
                        if(!ALLOW_ISOLATED_KEY.enabledIn(parserFeature)){
                            throw new IOException("Missing '=' between key and (null)value");
                        }
                        //NULL Value
                        currentToken = SegmentToken.END_SUB_ENTRY;
                    })
                    .when(END_ENTRY::isSame)
                    .then(() -> {
                        //Missing '=' core value
                        if(!ALLOW_ISOLATED_KEY.enabledIn(parserFeature)){
                            throw new IOException("Missing '=' between key and (null)value");
                        }
                        //NULL Value
                        currentToken = SegmentToken.END_ENTRY;
                    })

                    //key&&
                    //也许是缺少= START_OBJECT_VALUE
                    //也许是NullValue END_OBJECT_VALUE
                    .when(START_OBJECT_VALUE::isStart)
                    .then()
                        .next()
                            .when(START_OBJECT_VALUE::isStart)
                            .then(() -> {
                                if(path.empty()){
                                    //Missing '='
                                    if(!ALLOW_KEY_NOT_CLOSED.enabledIn(parserFeature)){
                                        throw new IOException("Missing '=' between key and (object)value");
                                    }
                                    //Object Value
                                    currentToken = SegmentToken.START_OBJECT_VALUE;
                                }else{
                                    //NULL Value
                                    currentToken = SegmentToken.END_OBJECT_VALUE;
                                }
                            })
                            .done()
                        .back()
                    .then(() -> {
                        if(!IGNORE_INVAILD_SYMBOL.enabledIn(parserFeature)){
                            throw new IOException("Invaild symbol '&' in key");
                        }
                        //Ignore Invaild symbol
                    })
                    .done()
                .read(buffer);

        if(currentToken == END_OBJECT_VALUE){
            path.pop();
        }

        if(len == 0){
            return null;
        }

        buffer.rewind();
        String result = buffer.toString().substring(0,len);
        if(currentToken == START_OBJECT_VALUE){
            path.push(result);
        }
        lastKey = result;
        return result;
    }

    /**
     * 读取VALUE
     * @return VALUE
     * @throws IOException
     */
    public String readValue() throws IOException {
        return readValue(false);
    }

    /**
     * 读取对象VALUE
     * @return 对象VALUE
     * @throws IOException
     */
    public String readObjectValue() throws IOException {
        return readValue(true);
    }


    /**
     * 读取VALUE
     * @param onlyObject true：仅遇到‘&&’结束
     * @return VALUE
     * @throws IOException
     */
    private String readValue(boolean onlyObject) throws IOException {
        switch (currentToken) {
            case END_ENTRY: case END_SUB_ENTRY: case END_PART_KEY: case END_OBJECT_VALUE:
                throw new IOException("当前状态不能读取值: " + currentToken);
            case START_OBJECT_VALUE:
                if (!onlyObject) throw new IOException("对象值必须使用 readObjectValue");
                break;
            default: break;
        }
        StringBuilder value = new StringBuilder();
        boolean quotedLog = false;
        int depth = 0;
        while (true) {
            int c = reader.read();
            if (c < 0) {
                if (quotedLog) throw new IOException("日志缺少闭合 //");
                currentToken = NULL_VALUE;
                break;
            }
            if (c == '/') {
                int next = reader.read();
                String key = lastKey;
                if (onlyObject && value.length() > 0 && value.charAt(value.length()-1) == '=') {
                    int start = Math.max(value.lastIndexOf(";"), value.lastIndexOf(",")) + 1;
                    key = value.substring(start, value.length()-1);
                }
                boolean logStart = key != null && key.matches("([A-Za-z0-9]+-)?i[0-9a-z]1[0-9a-z]{3}(-Info)?")
                    && ((!onlyObject && value.length() == 0) || (onlyObject && value.length() > 0 && value.charAt(value.length()-1) == '='));
                if (next == '/' && (quotedLog || logStart)) {
                    quotedLog = !quotedLog;
                    value.append("//");
                    continue;
                }
                if (next >= 0) reader.unread(next);
            }
            if (!quotedLog && c == '&') {
                int next = reader.read();
                if (next == '&') {
                    if (depth > 0) { depth--; value.append("&&"); continue; }
                    currentToken = END_OBJECT_VALUE;
                    if (!path.empty()) path.pop();
                    break;
                }
                if (next >= 0) reader.unread(next);
            }
            if (!quotedLog && onlyObject && c == '=') {
                int first = reader.read(), second = first < 0 ? -1 : reader.read();
                if (first == '&' && second == '&') {
                    depth++; value.append("=&&"); continue;
                }
                if (second >= 0) reader.unread(second);
                if (first >= 0) reader.unread(first);
            }
            if (!quotedLog && !onlyObject && (c == ';' || c == ',')) {
                currentToken = c == ';' ? END_ENTRY : END_SUB_ENTRY;
                break;
            }
            value.append((char)c);
        }
        return value.length() == 0 ? null : value.toString();
    }

    /**
     * 当前Token
     * @return SegmentToken
     */
    public SegmentToken currentToken(){
        return currentToken;
    }

    public void initToken(){
        currentToken = START_OBJECT_VALUE;
    }

    public void setParserFeature(int parserFeature) {
        this.parserFeature = parserFeature;
    }

    @Override
    public void close(){
        try {
            reader.close();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void configured(Configurator<SegmentParser> by) {
        by.config(this);
    }

}
