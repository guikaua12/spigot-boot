///*
// * MIT License
// *
// * Copyright (c) 2023 Guilherme Kauã (Approximations)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// * SOFTWARE.
// */
//
//package me.approximations.apxPlugin.messaging.bungee.actions.normal;
//
//import lombok.Data;
//import lombok.EqualsAndHashCode;
//import me.approximations.apxPlugin.messaging.bungee.actions.MessageAction;
//import org.jetbrains.annotations.NotNull;
//
//import java.io.DataOutput;
//import java.io.IOException;
//import java.util.List;
//
//@EqualsAndHashCode(callSuper=true)
//@Data
//public class ForwardAction extends MessageAction {
//    public static final String SUB_CHANNEL = "Forward";
//    public static final String SERVER_ALL = "ALL";
//    public static final String SERVER_ONLINE = "ONLINE";
//
//    private final @NotNull String server;
//    private final @NotNull String subChannel;
//    private final @NotNull List<Object> objects;
//
//    public @NotNull String getSubChannel() {
//        return SUB_CHANNEL;
//    }
//
//    public void write(@NotNull DataOutput dataOutput) throws IOException {
//        dataOutput.writeUTF(SUB_CHANNEL);
//        dataOutput.writeUTF(server);
//        dataOutput.writeUTF(subChannel);
//    }
//
//    @Override
//    public void writeBody(@NotNull DataOutput dataOutput) throws IOException {
//        for (Object object : objects) {
//            if (object instanceof String) {
//                dataOutput.writeUTF((String) object);
//            } else if (object instanceof Integer) {
//                dataOutput.writeInt((Integer) object);
//            } else if (object instanceof Boolean) {
//                dataOutput.writeBoolean((Boolean) object);
//            } else if (object instanceof Double) {
//                dataOutput.writeDouble((Double) object);
//            } else if (object instanceof Float) {
//                dataOutput.writeFloat((Float) object);
//            } else if (object instanceof Long) {
//                dataOutput.writeLong((Long) object);
//            } else if (object instanceof Short) {
//                dataOutput.writeShort((Short) object);
//            } else if (object instanceof Byte) {
//                dataOutput.writeByte((Byte) object);
//            } else {
//                throw new IOException("Unknown object type: " + object.getClass().getName());
//            }
//        }
//    }
//
//}
