import com.strobel.decompiler.*;
import com.strobel.decompiler.languages.java.JavaLanguage;
import com.strobel.assembler.metadata.*;
import java.io.StringWriter;

public class TestFix {
    public static void main(String[] args) {
        try {
            // Set up decompiler
            DecompilerSettings settings = new DecompilerSettings();
            DecompilationOptions options = new DecompilationOptions();
            options.setSettings(settings);
            
            // Create metadata system
            ITypeLoader typeLoader = new ClasspathTypeLoader();
            MetadataSystem metadataSystem = new MetadataSystem(typeLoader);
            
            // Load our test class
            TypeReference typeRef = metadataSystem.lookupType("com.strobel.decompiler.TypeInferenceTests$G");
            
            if (typeRef == null) {
                System.err.println("Could not find test class TypeInferenceTests$G");
                return;
            }
            
            TypeDefinition type = typeRef.resolve();
            if (type == null) {
                System.err.println("Could not resolve test class TypeInferenceTests$G");
                return;
            }
            
            // Decompile it
            StringWriter output = new StringWriter();
            PlainTextOutput textOutput = new PlainTextOutput(output);
            
            JavaLanguage language = new JavaLanguage();
            language.decompileType(type, textOutput, options);
            
            String result = output.toString();
            System.out.println("Decompiled result:");
            System.out.println(result);
            
            // Check if our fix worked
            if (result.contains("throw (Exception)(Object)args;")) {
                System.out.println("\n✓ SUCCESS: Double cast preserved!");
            } else if (result.contains("throw (Exception)args;")) {
                System.out.println("\n✗ FAILED: Only single cast found");
            } else {
                System.out.println("\n? UNKNOWN: No relevant cast found");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}