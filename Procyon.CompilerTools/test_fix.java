import java.util.List;
import java.io.StringWriter;
import com.strobel.decompiler.*;
import com.strobel.decompiler.languages.java.JavaLanguage;
import com.strobel.assembler.metadata.*;

public class test_fix {
    
    @SuppressWarnings("UnusedParameters")
    private static class C {
        void test(final Object o) {
        }

        void test(final Integer i) {
        }

        void test(final int i) {
        }

        void t(final int x) {
            test(Integer.valueOf(x));
            test((Object) Integer.valueOf(x));
            test(x);
        }
    }

    public static void main(String[] args) {
        try {
            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            StringWriter output = new StringWriter();
            PlainTextOutput plainOutput = new PlainTextOutput(output);
            
            // Decompile the inner class C
            TypeReference typeRef = new MetadataSystem().lookupType("test_fix$C");
            DecompilerContext context = new DecompilerContext();
            context.setCurrentType(typeRef.resolve());
            context.setSettings(settings);
            
            JavaLanguage.getInstance().decompileType(typeRef.resolve(), plainOutput, context);
            
            String result = output.toString();
            System.out.println("Decompiled result:");
            System.out.println(result);
            
            // Check if our fix worked
            if (result.contains("test((Object)x)") || result.contains("test((Object) x)")) {
                System.out.println("\n✓ SUCCESS: Cast (Object) is preserved!");
            } else if (result.contains("test(x);") && !result.contains("test((Object)")) {
                System.out.println("\n✗ FAILED: Cast (Object) was removed incorrectly!");
            } else {
                System.out.println("\n? UNCLEAR: Could not determine test result");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}