package avrocompiler;

import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheCompiler;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.Scope;
import com.sampullara.util.FutureWriter;
import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.tool.Tool;
import org.apache.commons.lang.NotImplementedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Use mustache templates rather than print statements for generating Avro classes
 * <p/>
 * User: sam
 * Date: Jun 11, 2010
 * Time: 11:17:05 AM
 */

public class TemplatedSpecificCompiler {
  private final Set<Schema> queue = new HashSet<Schema>();
  private final Protocol protocol;

  /* List of Java reserved words from
   * http://java.sun.com/docs/books/jls/third_edition/html/lexical.html. */
  private static final Set<String> RESERVED_WORDS = new HashSet<String>(
      Arrays.asList(new String[] {
          "abstract", "assert", "boolean", "break", "byte", "case", "catch",
          "char", "class", "const", "continue", "default", "do", "double",
          "else", "enum", "extends", "false", "final", "finally", "float",
          "for", "goto", "if", "implements", "import", "instanceof", "int",
          "interface", "long", "native", "new", "null", "package", "private",
          "protected", "public", "return", "short", "static", "strictfp",
          "super", "switch", "synchronized", "this", "throw", "throws",
          "transient", "true", "try", "void", "volatile", "while"
        }));
  public static final MustacheCompiler MC = new MustacheCompiler();

  public TemplatedSpecificCompiler(Protocol protocol) {
    // enqueue all types
    for (Schema s : protocol.getTypes()) {
      enqueue(s);
    }
    this.protocol = protocol;
  }

  public TemplatedSpecificCompiler(Schema schema) {
    enqueue(schema);
    this.protocol = null;
  }
  
  /**
   * Captures output file path and contents.
   */
  static class OutputFile {
    String path;
    String contents;

    /**
     * Writes output to path destination directory, creating directories as
     * necessary.  Returns the created file.
     */
    File writeToDestination(File destDir) throws IOException {
      File f = new File(destDir, path);
      f.getParentFile().mkdirs();
      FileWriter fw = new FileWriter(f);
      try {
        fw.write(contents);
      } finally {
        fw.close();
      }
      return f;
    }
  }

  /**
   * Generates Java interface and classes for a protocol.
   * @param src the source Avro protocol file
   * @param dest the directory to place generated files in
   */
  public static void compileProtocol(File src, File dest) throws IOException {
    Protocol protocol = Protocol.parse(src);
    TemplatedSpecificCompiler compiler = new TemplatedSpecificCompiler(protocol);
    compiler.compileToDestination(dest);
  }

  /** Generates Java classes for a schema. */
  public static void compileSchema(File src, File dest) throws IOException {
    Schema schema = Schema.parse(src);
    TemplatedSpecificCompiler compiler = new TemplatedSpecificCompiler(schema);
    compiler.compileToDestination(dest);
  }

  static String mangle(String word) {
    if (RESERVED_WORDS.contains(word)) {
      return word + "$";
    }
    return word;
  }

  /** Recursively enqueue schemas that need a class generated. */
  private void enqueue(Schema schema) {
    if (queue.contains(schema)) return;
    switch (schema.getType()) {
    case RECORD:
      queue.add(schema);
      for (Schema.Field field : schema.getFields())
        enqueue(field.schema());
      break;
    case MAP:
      enqueue(schema.getValueType());
      break;
    case ARRAY:
      enqueue(schema.getElementType());
      break;
    case UNION:
      for (Schema s : schema.getTypes())
        enqueue(s);
      break;
    case ENUM:
    case FIXED:
      queue.add(schema);
      break;
    case STRING: case BYTES:
    case INT: case LONG:
    case FLOAT: case DOUBLE:
    case BOOLEAN: case NULL:
      break;
    default: throw new RuntimeException("Unknown type: "+schema);
    }
  }

  /** Generate java classes for enqueued schemas. */
  Collection<OutputFile> compile() {
    List<OutputFile> out = new ArrayList<OutputFile>();
    for (Schema schema : queue) {
      out.add(compile(schema));
    }
    if (protocol != null) {
      out.add(compileInterface(protocol));
    }
    return out;
  }

  private OutputFile compileInterface(Protocol protocol) {
    throw new NotImplementedException();
  }

  private void compileToDestination(File dst) throws IOException {
    for (Schema schema : queue) {
      OutputFile o = compile(schema);
      o.writeToDestination(dst);
    }
    if (protocol != null) {
      compileInterface(protocol).writeToDestination(dst);
    }
  }

  private OutputFile compile(Schema schema) {
    OutputFile outputFile = new OutputFile();
    String name = mangle(schema.getName());
    outputFile.path = makePath(name, schema.getNamespace());
    StringWriter out = new StringWriter();
    switch (schema.getType()) {
    case RECORD:
      try {
        Mustache mustache = createMustache("/record.mustache");
        Scope scope = new Scope();
        scope.put("className", name);
        scope.put("packageName", schema.getNamespace());
        scope.put("schemaText", schema.toString().replace("\"", "\\\""));
        List<Schema.Field> fieldList = schema.getFields();
        List fields = new ArrayList(fieldList.size());
        for (final Schema.Field field : fieldList) {
          fields.add(new Object() {
            int num = field.pos();
            String type = unbox(field.schema());
            String name = mangle(field.name());
          });
        }
        scope.put("fields", fields);
        execute(out, mustache, scope);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      break;
    case ENUM:
      try {
        Mustache mustache = createMustache("/enum.mustache");
        Scope scope = new Scope();
        scope.put("enumName", name);
        scope.put("packageName", schema.getNamespace());
        final List<String> fieldList = schema.getEnumSymbols();
        List values = new ArrayList(fieldList.size());
        int i = 0;
        for (final String value : fieldList) {
          final boolean isLast = (++i == fieldList.size());
          values.add(new Object() {
            String name = mangle(value);
            boolean last = isLast;
          });
        }
        scope.put("values", values);
        execute(out, mustache, scope);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      break;
    case FIXED:
      break;
    case MAP: case ARRAY: case UNION: case STRING: case BYTES:
    case INT: case LONG: case FLOAT: case DOUBLE: case BOOLEAN: case NULL:
      break;
    default: throw new RuntimeException("Unknown type: "+schema);
    }

    outputFile.contents = out.toString();
    return outputFile;
  }

  private void execute(StringWriter out, Mustache mustache, Scope scope) throws MustacheException, IOException {
    FutureWriter futureWriter = new FutureWriter(out);
    mustache.execute(futureWriter, scope);
    futureWriter.flush();
  }

  private Mustache createMustache(String template) throws MustacheException {
    return MC.compile(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(template))),
            new Stack<String>(), new AtomicInteger(0), getClass().getClassLoader());
  }

  private String unbox(Schema schema) {
    switch (schema.getType()) {
    case INT:     return "int";
    case LONG:    return "long";
    case FLOAT:   return "float";
    case DOUBLE:  return "double";
    case BOOLEAN: return "boolean";
    default:      return type(schema);
    }
  }

  static String makePath(String name, String space) {
    if (space == null || space.isEmpty()) {
      return name + ".java";
    } else {
      return space.replace('.', File.separatorChar) + File.separatorChar
        + name + ".java";
    }
  }

  private String type(Schema schema) {
    switch (schema.getType()) {
    case RECORD:
    case ENUM:
    case FIXED:
      return mangle(schema.getFullName());
    case ARRAY:
      return "org.apache.avro.generic.GenericArray<"+type(schema.getElementType())+">";
    case MAP:
      return "java.util.Map<org.apache.avro.util.Utf8,"+type(schema.getValueType())+">";
    case UNION:
      List<Schema> types = schema.getTypes();     // elide unions with null
      if ((types.size() == 2) && types.contains(NULL_SCHEMA))
        return type(types.get(types.get(0).equals(NULL_SCHEMA) ? 1 : 0));
      return "java.lang.Object";
    case STRING:  return "org.apache.avro.util.Utf8";
    case BYTES:   return "java.nio.ByteBuffer";
    case INT:     return "java.lang.Integer";
    case LONG:    return "java.lang.Long";
    case FLOAT:   return "java.lang.Float";
    case DOUBLE:  return "java.lang.Double";
    case BOOLEAN: return "java.lang.Boolean";
    case NULL:    return "java.lang.Void";
    default: throw new RuntimeException("Unknown type: "+schema);
    }
  }

  private static final Schema NULL_SCHEMA = Schema.create(Schema.Type.NULL);

  /**
   * Implementation of Tool for inclusion by the "avro-tools" runner.
   */
  public static class SpecificCompilerTool implements Tool {
    @Override
    public int run(InputStream in, PrintStream out, PrintStream err,
        List<String> args) throws Exception {
      if (args.size() != 3) {
        System.err.println("Expected 3 arguments: (schema|protocol) inputfile outputdir");
        return 1;
      }
      String method = args.get(0);
      File input = new File(args.get(1));
      File output = new File(args.get(2));
      if ("schema".equals(method)) {
        compileSchema(input, output);
      } else if ("protocol".equals(method)) {
        compileProtocol(input, output);
      } else {
        System.err.println("Expected \"schema\" or \"protocol\".");
        return 1;
      }
      return 0;
    }

    @Override
    public String getName() {
      return "compile";
    }

    @Override
    public String getShortDescription() {
      return "Generates Java code for the given schema.";
    }
  }
  
}
