/*
 * Copyright 2015 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.NativeBytes;
import net.openhft.chronicle.bytes.NoBytesStore;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.time.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static net.openhft.chronicle.bytes.NativeBytes.nativeBytes;
import static org.junit.Assert.*;

public class TextWire2Test {

    Bytes bytes;

    @Test
    public void testWrite() {
        Wire wire = createWire();
        wire.write();
        wire.write();
        wire.write();
        wire.flip();
        assertEquals("\"\": \"\": \"\": ", wire.toString());
    }

    private TextWire createWire() {
        bytes = nativeBytes();
        return new TextWire(bytes);
    }

    @Test
    public void testSimpleBool() {
        Wire wire = createWire();

        wire.write(() -> "F").bool(false);
        wire.write(() -> "T").bool(true);
        wire.flip();
        assertEquals("F: false\n" +
                "T: true\n", wire.toString());
        String expected = "{F=false, T=true}";
        expectWithSnakeYaml(expected, wire);

        assertEquals(false, wire.read(() -> "F").bool());
        assertEquals(true, wire.read(() -> "T").bool());
    }

    private void expectWithSnakeYaml(String expected, Wire wire) {
        String s = wire.toString();
        Object load = null;
        try {
            Yaml yaml = new Yaml();
            load = yaml.load(new StringReader(s));
        } catch (Exception e) {
            System.out.println(s);
            throw e;
        }
        assertEquals(expected, load.toString());
    }

    @Test
    public void testInt64() {
        Wire wire = createWire();
        long expected = 1234567890123456789L;
        wire.write(() -> "VALUE").int64(expected);
        wire.flip();
        expectWithSnakeYaml("{VALUE=1234567890123456789}", wire);
        assertEquals(expected, wire.read(() -> "VALUE").int64());

    }

    @Test
    public void testInt16() {
        Wire wire = createWire();
        short expected = 12345;
        wire.write(() -> "VALUE").int64(expected);
        wire.flip();
        expectWithSnakeYaml("{VALUE=12345}", wire);
        assertEquals(expected, wire.read(() -> "VALUE").int16());
    }

    @Test(expected = IllegalStateException.class)
    public void testInt16TooLarge() {
        Wire wire = createWire();
        wire.write(() -> "VALUE").int64(Long.MAX_VALUE);
        wire.flip();
        wire.read(() -> "VALUE").int16();
    }

    @Test
    public void testInt32() {
        Wire wire = createWire();
        int expected = 1;
        wire.write(() -> "VALUE").int64(expected);
        wire.write(() -> "VALUE2").int64(expected);
        wire.flip();
        expectWithSnakeYaml("{VALUE=1, VALUE2=1}", wire);
//        System.out.println("out" + Bytes.toHex(wire.bytes()));
        assertEquals(expected, wire.read(() -> "VALUE").int16());
        assertEquals(expected, wire.read(() -> "VALUE2").int16());
    }

    @Test(expected = IllegalStateException.class)
    public void testInt32TooLarge() {
        Wire wire = createWire();
        wire.write(() -> "VALUE").int64(Integer.MAX_VALUE);
        wire.flip();
        wire.read(() -> "VALUE").int16();
    }

    @Test
    public void testWrite1() {
        Wire wire = createWire();
        wire.write(BWKey.field1);
        wire.write(BWKey.field2);
        wire.write(BWKey.field3);
        wire.flip();
        assertEquals("field1: field2: field3: ", wire.toString());
    }

    @Test
    public void testWrite2() {
        Wire wire = createWire();
        wire.write(() -> "Hello");
        wire.write(() -> "World");
        wire.write(() -> "Long field name which is more than 32 characters, Bye");
        wire.flip();
        assertEquals("Hello: World: \"Long field name which is more than 32 characters, Bye\": ", wire.toString());
    }

    @Test
    public void testRead() {
        Wire wire = createWire();
        wire.write();
        wire.write(BWKey.field1);
        wire.write(() -> "Test");
        wire.flip();
        wire.read();
        wire.read();
        wire.read();
        assertEquals(1, bytes.remaining());
        // check it's safe to read too much.
        wire.read();
    }

    @Test
    public void testRead1() {
        Wire wire = createWire();
        wire.write();
        wire.write(BWKey.field1);
        wire.write(() -> "Test");
        wire.flip();

        // ok as blank matches anything
        wire.read(BWKey.field1);
        wire.read(BWKey.field1);
        // not a match
        try {
            wire.read(BWKey.field1);
            fail();
        } catch (UnsupportedOperationException expected) {
            StringBuilder name = new StringBuilder();
            wire.read(name);
            assertEquals("Test", name.toString());
        }
        assertEquals(1, bytes.remaining());
        // check it's safe to read too much.
        wire.read();
    }

    @Test
    public void testRead2() {
        Wire wire = createWire();
        wire.write();
        wire.write(BWKey.field1);
        String name1 = "Long field name which is more than 32 characters, Bye";
        wire.write(() -> name1);
        wire.flip();

        // ok as blank matches anything
        StringBuilder name = new StringBuilder();
        wire.read(name);
        assertEquals(0, name.length());

        wire.read(name);
        assertEquals(BWKey.field1.name(), name.toString());

        wire.read(name);
        assertEquals(name1, name.toString());

        assertEquals(1, bytes.remaining());
        // check it's safe to read too much.
        wire.read();
    }

    @Test
    public void int8() {
        Wire wire = createWire();
        wire.write().int8(1);
        wire.write(BWKey.field1).int8(2);
        wire.write(() -> "Test").int8(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicInteger i = new AtomicInteger();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().int8(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void int16() {
        Wire wire = createWire();
        wire.write().int16(1);
        wire.write(BWKey.field1).int16(2);
        wire.write(() -> "Test").int16(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicInteger i = new AtomicInteger();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().int16(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void uint8() {
        Wire wire = createWire();
        wire.write().uint8(1);
        wire.write(BWKey.field1).uint8(2);
        wire.write(() -> "Test").uint8(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicInteger i = new AtomicInteger();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().uint8(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void uint16() {
        Wire wire = createWire();
        wire.write().uint16(1);
        wire.write(BWKey.field1).uint16(2);
        wire.write(() -> "Test").uint16(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicInteger i = new AtomicInteger();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().uint16(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void uint32() {
        Wire wire = createWire();
        wire.write().uint32(1);
        wire.write(BWKey.field1).uint32(2);
        wire.write(() -> "Test").uint32(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicLong i = new AtomicLong();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().uint32(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void int32() {
        Wire wire = createWire();
        wire.write().int32(1);
        wire.write(BWKey.field1).int32(2);
        wire.write(() -> "Test").int32(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicInteger i = new AtomicInteger();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().int32(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void int64() {
        Wire wire = createWire();
        wire.write().int64(1);
        wire.write(BWKey.field1).int64(2);
        wire.write(() -> "Test").int64(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        AtomicLong i = new AtomicLong();
        IntConsumer ic = i::set;
        LongStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().int64(i::set);
            assertEquals(e, i.get());
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();

    }

    @Test
    public void float64() {
        Wire wire = createWire();
        wire.write().float64(1);
        wire.write(BWKey.field1).float64(2);
        wire.write(() -> "Test").float64(3);
        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
        assertEquals("\"\": 1\n" +
                "field1: 2\n" +
                "Test: 3\n", wire.toString());

        // ok as blank matches anything
        class Floater {
            double f;

            public void set(double d) {
                f = d;
            }
        }
        Floater n = new Floater();
        IntStream.rangeClosed(1, 3).forEach(e -> {
            wire.read().float64(n::set);
            assertEquals(e, n.f, 0.0);
        });

        assertEquals(0, bytes.remaining());
        // check it's safe to read too much.
        wire.read();
    }

    @Test
    public void text() {
        Wire wire = createWire();
        wire.write().text("Hello");
        wire.write(BWKey.field1).text("world");
        String name1 = "Long field name which is more than 32 characters, \\ \nBye";

        wire.write(() -> "Test")
                .text(name1);
        wire.flip();
        expectWithSnakeYaml("{=Hello, field1=world, Test=Long field name which is more than 32 characters, \\ \n" +
                "Bye}", wire);
        assertEquals("\"\": Hello\n" +
                "field1: world\n" +
                "Test: \"Long field name which is more than 32 characters, \\\\ \\nBye\"\n", wire.toString());

        // ok as blank matches anything
        StringBuilder sb = new StringBuilder();
        Stream.of("Hello", "world", name1).forEach(e -> {
            wire.read()
                    .text(sb);
            assertEquals(e, sb.toString());
        });

        assertEquals(1, bytes.remaining());
        // check it's safe to read too much.
        wire.read();
    }

    @Test
    public void type() {
        Wire wire = createWire();
        wire.write().type("MyType");
        wire.write(BWKey.field1).type("AlsoMyType");
        String name1 = "com.sun.java.swing.plaf.nimbus.InternalFrameInternalFrameTitlePaneInternalFrameTitlePaneMaximizeButtonWindowNotFocusedState";
        wire.write(() -> "Test").type(name1);
        wire.writeComment("");
        wire.flip();
        // TODO fix how types are serialized.
//        expectWithSnakeYaml(wire, "{=1, field1=2, Test=3}");
        assertEquals("\"\": !MyType " +
                "field1: !AlsoMyType " +
                "Test: !" + name1 + " # \n", wire.toString());

        // ok as blank matches anything
        StringBuilder sb = new StringBuilder();
        Stream.of("MyType", "AlsoMyType", name1).forEach(e -> {
            wire.read().type(sb);
            assertEquals(e, sb.toString());
        });

        assertEquals(3, bytes.remaining());
        // check it's safe to read too much.
        wire.read();
    }

    @Test
    public void testBool() {
        Wire wire = createWire();
        wire.write().bool(false)
                .write().bool(true)
                .write().bool(null);
        wire.flip();
        wire.read().bool(Assert::assertFalse)
                .read().bool(Assert::assertTrue)
                .read().bool(Assert::assertNull);
    }

    @Test
    public void testFloat32() {
        Wire wire = createWire();
        wire.write().float32(0.0F)
                .write().float32(Float.NaN)
                .write().float32(Float.POSITIVE_INFINITY)
                .write().float32(Float.NEGATIVE_INFINITY)
                .write().float32(123456.0f);
        wire.flip();
        System.out.println(wire.bytes());
        wire.read().float32(t -> assertEquals(0.0F, t, 0.0F))
                .read().float32(t -> assertTrue(Float.isNaN(t)))
                .read().float32(t -> assertEquals(Float.POSITIVE_INFINITY, t, 0.0F))
                .read().float32(t -> assertEquals(Float.NEGATIVE_INFINITY, t, 0.0F))
                .read().float32(t -> assertEquals(123456.0f, t, 0.0F));
    }

    @Test
    public void testTime() {
        Wire wire = createWire();
        LocalTime now = LocalTime.now();
        wire.write().time(now)
                .write().time(LocalTime.MAX)
                .write().time(LocalTime.MIN);
        wire.flip();
        assertEquals("\"\": " + now + "\n" +
                        "\"\": 23:59:59.999999999\n" +
                        "\"\": 00:00\n",
                bytes.toString());
        wire.read().time(t -> assertEquals(now, t))
                .read().time(t -> assertEquals(LocalTime.MAX, t))
                .read().time(t -> assertEquals(LocalTime.MIN, t));

    }

    @Test
    public void testZonedDateTime() {
        Wire wire = createWire();
        ZonedDateTime now = ZonedDateTime.now();
        wire.write().zonedDateTime(now)
                .write().zonedDateTime(ZonedDateTime.of(LocalDateTime.MAX, ZoneId.systemDefault()))
                .write().zonedDateTime(ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault()));
        wire.flip();
        wire.read().zonedDateTime(t -> assertEquals(now, t))
                .read().zonedDateTime(t -> assertEquals(ZonedDateTime.of(LocalDateTime.MAX, ZoneId.systemDefault()), t))
                .read().zonedDateTime(t -> assertEquals(ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault()), t));
    }

    @Test
    public void testDate() {
        Wire wire = createWire();
        LocalDate now = LocalDate.now();
        wire.write().date(now)
                .write().date(LocalDate.MAX)
                .write().date(LocalDate.MIN);
        wire.flip();
        wire.read().date(t -> assertEquals(now, t))
                .read().date(t -> assertEquals(LocalDate.MAX, t))
                .read().date(t -> assertEquals(LocalDate.MIN, t));
    }

    @Test
    public void testUuid() {
        Wire wire = createWire();
        UUID uuid = UUID.randomUUID();
        wire.write().uuid(uuid)
                .write().uuid(new UUID(0, 0))
                .write().uuid(new UUID(Long.MAX_VALUE, Long.MAX_VALUE));
        wire.flip();
        wire.read().uuid(t -> assertEquals(uuid, t))
                .read().uuid(t -> assertEquals(new UUID(0, 0), t))
                .read().uuid(t -> assertEquals(new UUID(Long.MAX_VALUE, Long.MAX_VALUE), t));
    }

    @Test
    public void testBytes() {
        Wire wire = createWire();
        byte[] allBytes = new byte[256];
        for (int i = 0; i < 256; i++)
            allBytes[i] = (byte) i;
        wire.write().bytes(NoBytesStore.NO_BYTES)
                .write().bytes(Bytes.wrap("Hello".getBytes()))
                .write().bytes(Bytes.wrap("quotable, text".getBytes()))
                .write().bytes(allBytes);
        wire.flip();
        System.out.println(bytes.toString());
        NativeBytes allBytes2 = nativeBytes();
        wire.read().bytes(wi -> assertEquals(0, wi.bytes().remaining()))
                .read().bytes(wi -> assertEquals("Hello", wi.bytes().toString()))
                .read().bytes(wi -> assertEquals("quotable, text", wi.bytes().toString()))
                .read().bytes(allBytes2);
        allBytes2.flip();
        assertEquals(Bytes.wrap(allBytes), allBytes2);
    }

    @Test
    public void testWriteMarshallable() {
        Wire wire = createWire();
        MyTypes mtA = new MyTypes();
        mtA.b(true);
        mtA.d(123.456);
        mtA.i(-12345789);
        mtA.s((short) 12345);
        mtA.text.append("Hello World");

        wire.write(() -> "A").marshallable(mtA);

        MyTypes mtB = new MyTypes();
        mtB.b(false);
        mtB.d(123.4567);
        mtB.i(-123457890);
        mtB.s((short) 1234);
        mtB.text.append("Bye now");
        wire.write(() -> "B").marshallable(mtB);

        wire.flip();
        assertEquals("A: {\n" +
                "  B_FLAG: true,\n" +
                "  S_NUM: 12345,\n" +
                "  D_NUM: 123.456,\n" +
                "  L_NUM: 0,\n" +
                "  I_NUM: -12345789,\n" +
                "  TEXT: Hello World\n" +
                "}\n" +
                "B: {\n" +
                "  B_FLAG: false,\n" +
                "  S_NUM: 1234,\n" +
                "  D_NUM: 123.4567,\n" +
                "  L_NUM: 0,\n" +
                "  I_NUM: -123457890,\n" +
                "  TEXT: Bye now\n" +
                "}\n", wire.bytes().toString());
        expectWithSnakeYaml("{A={B_FLAG=true, S_NUM=12345, D_NUM=123.456, L_NUM=0, I_NUM=-12345789, TEXT=Hello World}, " +
                "B={B_FLAG=false, S_NUM=1234, D_NUM=123.4567, L_NUM=0, I_NUM=-123457890, TEXT=Bye now}}", wire);

        MyTypes mt2 = new MyTypes();
        wire.read(() -> "A").marshallable(mt2);
        assertEquals(mt2, mtA);

        wire.read(() -> "B").marshallable(mt2);
        assertEquals(mt2, mtB);
    }

    @Test
    public void testWriteMarshallableAndFieldLength() {
        Wire wire = createWire();
        MyTypes mtA = new MyTypes();
        mtA.b(true);
        mtA.d(123.456);
        mtA.i(-12345789);
        mtA.s((short) 12345);

        ValueOut write = wire.write(() -> "A");

        long start = wire.bytes().position() + 1; // including one space for "sep".
        write.marshallable(mtA);
        long fieldLen = wire.bytes().position() - start;

        wire.flip();
        expectWithSnakeYaml("{A={B_FLAG=true, S_NUM=12345, D_NUM=123.456, L_NUM=0, I_NUM=-12345789, TEXT=}}", wire);

        ValueIn read = wire.read(() -> "A");

        long len = read.readLength();

        assertEquals(fieldLen, len, 1);
    }


    @Test
    public void testMapReadAndWriteStrings() {
        final Bytes bytes = nativeBytes();
        final Wire wire = new TextWire(bytes);

        final Map<String, String> expected = new LinkedHashMap<>();

        expected.put("hello", "world");
        expected.put("hello1", "world1");
        expected.put("hello2", "world2");

        wire.writeDocument(false, o -> {
            o.write(() -> "example")
                    .map(expected);
        });
        wire.flip();
        bytes.position(4);
//        expectWithSnakeYaml("{example={hello=world, hello1=world1, hello2=world2}}", wire);
bytes.position(0);
        assertEquals("--- !!data\n" +
                        "example: !!seqmap [\n" +
                        "  { key: hello,\n" +
                        "    value: world },\n" +
                        "  { key: hello1,\n" +
                        "    value: world1 },\n" +
                        "  { key: hello2,\n" +
                        "    value: world2 }\n" +
                        "]\n",
                Wires.fromSizePrefixedBlobs(bytes));
        final Map<String, String> actual = new LinkedHashMap<>();
        wire.readDocument(null, c -> c.read(() -> "example").map(actual));
        assertEquals(actual, expected);
    }

    @Test
    @Ignore
    public void testMapReadAndWriteIntegers() {
        final Bytes bytes = nativeBytes();
        final Wire wire = new TextWire(bytes);

        final Map<Integer, Integer> expected = new HashMap<>();

        expected.put(1, 2);
        expected.put(2, 2);
        expected.put(3, 3);

        wire.writeDocument(false, o -> {
            o.write(() -> "example").map(expected);
        });

        wire.flip();
        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);

        assertEquals("--- !!data\n" +
                "example: !!map {" +
                "  ? !!int 1\n" +
                "  : !! int 1\n" +
                "  ? !!int 2\n" +
                "  : !!int 2\n" +
                "  ? !!int 3:\n" +
                "  : !!int 3\n" +
                "}\n", Wires.fromSizePrefixedBlobs(bytes));
        final Map<Integer, Integer> actual = new HashMap<>();
        wire.readDocument(null, c -> {
            Map m = c.read(() -> "example").map(Integer.class, Integer.class, actual);
            assertEquals(m, expected);
        });

    }


    class MyMarshallable implements Marshallable {

        String someData;

        public MyMarshallable(String someData) {
            this.someData = someData;
        }

        @Override
        public void writeMarshallable(WireOut wire) {
            wire.write(() -> "MyField").text(someData);
        }

        @Override
        public void readMarshallable(WireIn wire) throws IllegalStateException {
            someData = wire.read(() -> "MyField").text();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MyMarshallable that = (MyMarshallable) o;

            if (someData != null ? !someData.equals(that.someData) : that.someData != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            return someData != null ? someData.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "MyMarshable{" + "someData='" + someData + '\'' + '}';
        }
    }


    @Test
    public void testMapReadAndWriteMarshable() {

        final Bytes bytes = nativeBytes();
        final Wire wire = new TextWire(bytes);

        final Map<Marshallable, Marshallable> expected = new LinkedHashMap<>();

        expected.put(new MyMarshallable("aKey"), new MyMarshallable("aValue"));
        expected.put(new MyMarshallable("aKey2"), new MyMarshallable("aValue2"));

        wire.writeDocument(false, o -> o.write(() -> "example").map(expected));

        wire.flip();

        assertEquals("--- !!data\n" +
                "example: !!seqmap [\n" +
                "  { key: !net.openhft.chronicle.wire.TextWire2Test$MyMarshallable { MyField: aKey },\n" +
                "    value: !net.openhft.chronicle.wire.TextWire2Test$MyMarshallable { MyField: aValue } },\n" +
                "  { key: !net.openhft.chronicle.wire.TextWire2Test$MyMarshallable { MyField: aKey2 },\n" +
                "    value: !net.openhft.chronicle.wire.TextWire2Test$MyMarshallable { MyField: aValue2 } }\n" +
                "]\n", Wires.fromSizePrefixedBlobs(bytes));
        final Map<MyMarshallable, MyMarshallable> actual = new LinkedHashMap<>();

        wire.readDocument(null, c -> c.read(() -> "example")
                .map(
                MyMarshallable.class,
                        MyMarshallable.class,
                        actual));

        assertEquals(expected, actual);
    }

    enum BWKey implements WireKey {
        field1, field2, field3
    }

    @Test
    public void testException() {
        Exception e = new NullPointerException("Reference cannot be null") {
            @Override
            public StackTraceElement[] getStackTrace() {
                StackTraceElement[] stack = {
                        new StackTraceElement("net.openhft.chronicle.wire.TextWire2Test", "testException", "TextWire2Test.java", 783),
                        new StackTraceElement("sun.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2)
                };
                return stack;
            }
        };
        final Bytes bytes = nativeBytes();
        final Wire wire = new TextWire(bytes);
        wire.writeDocument(false, w -> w.writeEventName(() -> "exception").object(e));
        bytes.flip();
        System.out.println(Wires.fromSizePrefixedBlobs(bytes));
//        bytes.position(4);
        // SnakeYaml doesn't support single ! types.
//        expectWithSnakeYaml("{=1, field1=2, Test=3}", wire);
//        bytes.position(0);

        assertEquals("--- !!data\n" +
                "exception: !" + e.getClass().getName() + " {\n" +
                "  message: Reference cannot be null,\n" +
                "  stackTrace: [\n" +
                "    { className: net.openhft.chronicle.wire.TextWire2Test, methodName: testException, fileName: TextWire2Test.java, lineNumber: 783 },\n" +
                "    { className: sun.reflect.NativeMethodAccessorImpl, methodName: invoke0, fileName: NativeMethodAccessorImpl.java, lineNumber: -2 }\n" +
                "  ]\n" +
                "}\n", Wires.fromSizePrefixedBlobs(bytes));
    }
}
