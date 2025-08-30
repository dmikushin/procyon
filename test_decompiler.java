import com.strobel.decompiler.*;
import com.strobel.assembler.metadata.*;
import java.io.*;

public class TestDecompiler {
    public static void main(String[] args) {
        try {
            DecompilerSettings settings = new DecompilerSettings();
            DecompilationOptions options = new DecompilationOptions();
            options.setSettings(settings);
            
            // Load the class file
            ITypeLoader typeLoader = new ClasspathTypeLoader();
            MetadataSystem metadataSystem = new MetadataSystem(typeLoader);
            
            TypeReference type = metadataSystem.lookupType("TestThrow");
            StringWriter writer = new StringWriter();
            PlainTextOutput output = new PlainTextOutput(writer);
            
            Decompiler.decompile(type.getFullName(), output, options);
            System.out.println(writer.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}