import com.strobel.decompiler.languages.java.utilities.RedundantCastUtility;
import com.strobel.decompiler.languages.java.ast.*;
import com.strobel.decompiler.languages.java.JavaResolver;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.DecompilerSettings;

class DirectTest {
    public static void main(String[] args) {
        // Simple test to see if our debug output works
        try {
            // Create a minimal context for testing
            DecompilerSettings settings = new DecompilerSettings();
            DecompilerContext context = new DecompilerContext(settings);
            
            // Test our utility
            System.out.println("Testing RedundantCastUtility...");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}