/**
 * Test class with obfuscated patterns to test deobfuscation
 */
public class TestObfuscated {
    
    // Example 1: Control flow flattening
    public static int flattenedMethod(int input) {
        int state = 0;
        int result = 0;
        
        while (true) {
            switch (state) {
                case 0:
                    if (input > 10) {
                        state = 1;
                    } else {
                        state = 2;
                    }
                    break;
                case 1:
                    result = input * 2;
                    state = 3;
                    break;
                case 2:
                    result = input + 5;
                    state = 3;
                    break;
                case 3:
                    result = result + 1;
                    return result;
            }
        }
    }
    
    // Example 2: Opaque predicates
    public static void opaquePredicateExample(int x, int y) {
        // Always true: x*x >= 0
        if (x * x >= 0) {
            System.out.println("This always executes");
        }
        
        // Always false: x != x
        if (x != x) {
            System.out.println("This never executes - dead code");
        }
        
        // Always true: (x | 1) >= 1
        if ((x | 1) >= 1) {
            System.out.println("This always executes too");
        }
        
        // Always false: (7*y*y - 1) % 7 == 0
        if ((7 * y * y - 1) % 7 == 0) {
            System.out.println("This is dead code");
        }
    }
    
    // Example 3: String obfuscation (simplified)
    private static String[] encryptedStrings = {
        "SGVsbG8=",  // "Hello" in Base64
        "V29ybGQ=",  // "World" in Base64
    };
    
    private static String decrypt(int index) {
        // Simplified - real obfuscators use more complex encryption
        byte[] bytes = java.util.Base64.getDecoder().decode(encryptedStrings[index]);
        return new String(bytes);
    }
    
    public static void stringObfuscationExample() {
        String s1 = decrypt(0);  // "Hello"
        String s2 = decrypt(1);  // "World"
        System.out.println(s1 + " " + s2);
    }
    
    // Example 4: Dead code insertion
    public static int deadCodeExample(int value) {
        int temp1 = value * 2;
        int temp2 = temp1 / 2;  // temp2 == value
        int temp3 = Math.abs(temp2 - value);  // Always 0
        
        if (temp3 != 0) {
            throw new RuntimeException("Never happens");
        }
        
        int result = value + 10;
        
        // Dead loop
        for (int i = 0; i < 0; i++) {
            result += i;  // Never executes
        }
        
        return result;
    }
    
    // Example 5: Method outlining
    public static int a(int x) {
        return b(c(x));
    }
    
    private static int b(int y) {
        return d(y, 10);
    }
    
    private static int c(int x) {
        return e(x, 2);
    }
    
    private static int d(int a, int b) {
        return a + b;
    }
    
    private static int e(int a, int b) {
        return a * b;
    }
    
    public static void main(String[] args) {
        System.out.println("Flattened result: " + flattenedMethod(15));
        opaquePredicateExample(5, 3);
        stringObfuscationExample();
        System.out.println("Dead code result: " + deadCodeExample(7));
        System.out.println("Outlined result: " + a(5));  // Should be 5*2+10 = 20
    }
}