package avrocompiler;

import org.apache.avro.specific.OldSpecificCompiler;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.*;

/**
 * Generate with the old and the new.
 * <p/>
 * User: sam
 * Date: Jun 11, 2010
 * Time: 12:48:13 PM
 */
public class ComparisonTest {

  @Test
  public void testCompiler() throws IOException {
    File olddest = new File("target/oldavro");
    OldSpecificCompiler.compileSchema(new File(getClass().getResource("/User.json").getFile()), olddest);
    File newdest = new File("target/avro");
    TemplatedSpecificCompiler.compileSchema(new File(getClass().getResource("/User.json").getFile()), newdest);
    String recordName = "bagcheck/User.java";
    assertEquals(readFile(new File(olddest, recordName)), readFile(new File(newdest, recordName)));
    String enumName = "bagcheck/GenderType.java";
    assertEquals(readFile(new File(olddest, enumName)), readFile(new File(newdest, enumName)));
  }

  @Test
  public void testCompilerVariant() throws IOException {
    File olddest = new File("target/oldavro");
    OldSpecificCompiler.compileSchema(new File(getClass().getResource("/User.json").getFile()), olddest);
    File newdest = new File("target/avro");
    TemplatedSpecificCompiler.compileSchema("strings", new File(getClass().getResource("/User.json").getFile()), newdest);
    String recordName = "bagcheck/User.java";
    assertNotSame(readFile(new File(olddest, recordName)), readFile(new File(newdest, recordName)));
    String enumName = "bagcheck/GenderType.java";
    assertEquals(readFile(new File(olddest, enumName)), readFile(new File(newdest, enumName)));
  }

  @Test
  public void testProtocol() throws IOException {
    File olddest = new File("target/oldavro");
    OldSpecificCompiler.compileProtocol(new File(getClass().getResource("/ProtocolTest.json").getFile()), olddest);
    File newdest = new File("target/avro");
    TemplatedSpecificCompiler.compileProtocol(new File(getClass().getResource("/ProtocolTest.json").getFile()), newdest);
    String recordName = "avrocompiler/test/API.java";
    assertEquals(readFile(new File(olddest, recordName)), readFile(new File(newdest, recordName)));
  }

  private static String readFile(File file) throws IOException {
    BufferedReader br = new BufferedReader(new FileReader(file));
    StringWriter sw = new StringWriter();
    char[] chars = new char[8192];
    int read;
    while ((read = br.read(chars)) != -1) {
      sw.write(chars, 0, read);
    }
    sw.flush();
    return sw.toString();
  }
}
