# AI Decompiler: Safe Renaming Strategy

## The Problem

Renaming classes, methods, and fields can break dependencies because:

1. **External Dependencies**: Other JARs/classes reference the obfuscated names
2. **Reflection**: Code might use `Class.forName("a")` or `getMethod("e")`  
3. **Serialization**: Serialized objects expect specific class/field names
4. **JNI**: Native code references specific method signatures
5. **Configuration Files**: XML/JSON configs might reference class names
6. **Classloader Issues**: Dynamic loading expects original names

## Solution: Multi-Level Renaming Strategy

### Level 1: Safe Local Renaming (DEFAULT)

Only rename elements that are **guaranteed safe**:

```java
public class AIRenamingStrategy {
    
    public enum RenamingScope {
        LOCAL_VARIABLES_ONLY,      // Safest - only local vars & params
        PRIVATE_MEMBERS,           // Safe - private fields/methods
        PACKAGE_PRIVATE,           // Usually safe within same JAR
        PROTECTED_MEMBERS,         // Risky - might have subclasses
        PUBLIC_API,                // Dangerous - breaks external deps
        FULL_RENAME                // Only for standalone analysis
    }
    
    private RenamingScope scope = RenamingScope.LOCAL_VARIABLES_ONLY;
    
    public boolean canRename(Element element) {
        switch (scope) {
            case LOCAL_VARIABLES_ONLY:
                return element instanceof LocalVariable || 
                       element instanceof Parameter;
                       
            case PRIVATE_MEMBERS:
                return element instanceof LocalVariable ||
                       element instanceof Parameter ||
                       (element.getModifiers().contains(Modifier.PRIVATE));
                       
            case PACKAGE_PRIVATE:
                return !element.getModifiers().contains(Modifier.PUBLIC) &&
                       !element.getModifiers().contains(Modifier.PROTECTED);
                       
            default:
                return false;
        }
    }
}
```

### Level 2: Mapping-Based Approach

Keep original names but provide mappings:

```java
public class RenamingMapper {
    private final Map<String, String> classNameMap = new HashMap<>();
    private final Map<String, Map<String, String>> methodNameMap = new HashMap<>();
    private final Map<String, Map<String, String>> fieldNameMap = new HashMap<>();
    
    /**
     * Generate a mapping file instead of actual renaming
     */
    public void generateMapping(String originalName, String suggestedName, ElementType type) {
        switch (type) {
            case CLASS:
                classNameMap.put(originalName, suggestedName);
                break;
            case METHOD:
                // Store as ClassName -> (methodName -> newName)
                break;
            case FIELD:
                // Store as ClassName -> (fieldName -> newName)
                break;
        }
    }
    
    /**
     * Export mapping to a file for reference
     */
    public void exportMapping(File outputFile) {
        // Export as JSON/XML/Properties file
        // Example output:
        // {
        //   "classes": {
        //     "a": "ConfigurationManager",
        //     "b": "DataProcessor"
        //   },
        //   "methods": {
        //     "a.e()": "getInstance()",
        //     "b.f(String)": "processData(String)"
        //   }
        // }
    }
}
```

### Level 3: Comment-Based Documentation

Instead of renaming, add comments with suggested names:

```java
public class AIDocumentationTransform implements IAstTransform {
    
    @Override
    public void visitTypeDeclaration(TypeDeclaration node, Void data) {
        String obfuscatedName = node.getName();
        String suggestedName = aiClient.suggestClassName(obfuscatedName, context);
        
        // Don't rename, just document
        Comment comment = new Comment(
            String.format("AI Suggested Name: %s", suggestedName),
            CommentType.Block
        );
        node.insertChildBefore(comment, node.getFirstChild());
        
        // Keep original name
        // public class a {  // AI Suggested Name: ConfigurationManager
    }
    
    @Override  
    public void visitMethodDeclaration(MethodDeclaration node, Void data) {
        if (isObfuscated(node.getName())) {
            String suggested = aiClient.suggestMethodName(node);
            
            // Add inline comment
            node.addAnnotation(new MarkerAnnotation(
                "@AIName(\"" + suggested + "\")"
            ));
            
            // Result:
            // @AIName("getInstance")
            // public static a e() { ... }
        }
    }
}
```

### Level 4: Wrapper Generation

Generate wrapper classes with readable names:

```java
public class WrapperGenerator {
    
    /**
     * Generate a wrapper class with readable names that delegates to obfuscated class
     */
    public String generateWrapper(TypeDefinition obfuscatedClass, Map<String, String> nameMapping) {
        StringBuilder wrapper = new StringBuilder();
        
        String wrapperName = nameMapping.get(obfuscatedClass.getSimpleName());
        wrapper.append("/**\n");
        wrapper.append(" * Readable wrapper for obfuscated class: ").append(obfuscatedClass.getSimpleName()).append("\n");
        wrapper.append(" * Auto-generated by AI Decompiler\n");
        wrapper.append(" */\n");
        wrapper.append("public class ").append(wrapperName).append(" {\n");
        wrapper.append("    private final ").append(obfuscatedClass.getSimpleName()).append(" delegate;\n\n");
        
        // Generate constructor
        wrapper.append("    public ").append(wrapperName).append("() {\n");
        wrapper.append("        this.delegate = new ").append(obfuscatedClass.getSimpleName()).append("();\n");
        wrapper.append("    }\n\n");
        
        // Generate delegating methods with readable names
        for (MethodDefinition method : obfuscatedClass.getDeclaredMethods()) {
            String readableName = nameMapping.get(method.getName());
            wrapper.append("    public ").append(method.getReturnType()).append(" ");
            wrapper.append(readableName).append("(");
            // Add parameters...
            wrapper.append(") {\n");
            wrapper.append("        return delegate.").append(method.getName()).append("(");
            // Add parameter names...
            wrapper.append(");\n");
            wrapper.append("    }\n\n");
        }
        
        wrapper.append("}\n");
        return wrapper.toString();
    }
}
```

## Recommended Approach: Hybrid Strategy

```java
public class HybridRenamingStrategy {
    
    public void enhance(CompilationUnit unit, AIServiceClient ai) {
        // 1. Always safe: Rename local variables and parameters
        renameLocalElements(unit, ai);
        
        // 2. Generate mapping file for classes/methods/fields
        Map<String, String> mapping = generateNameMapping(unit, ai);
        exportMapping(mapping, "name-mapping.json");
        
        // 3. Add documentation comments with suggested names
        addDocumentationComments(unit, mapping);
        
        // 4. Optionally generate wrapper classes for key obfuscated classes
        if (settings.generateWrappers) {
            generateWrapperClasses(unit, mapping);
        }
        
        // 5. Add a header comment with usage instructions
        addMappingInstructions(unit, mapping);
    }
    
    private void addMappingInstructions(CompilationUnit unit, Map<String, String> mapping) {
        String instructions = 
            "/*\n" +
            " * AI-Enhanced Decompilation Results\n" +
            " * ==================================\n" +
            " * \n" +
            " * Original obfuscated names have been preserved to maintain compatibility.\n" +
            " * \n" +
            " * Suggested readable names:\n" +
            " *   Class 'a' -> 'ConfigurationManager'\n" +
            " *   Method 'e()' -> 'getInstance()'\n" +
            " *   Field 'b' -> 'instance'\n" +
            " * \n" +
            " * For detailed mapping, see: name-mapping.json\n" +
            " * For wrapper classes, see: generated-wrappers/\n" +
            " */\n";
        
        unit.insertChildBefore(new Comment(instructions), unit.getFirstChild());
    }
}
```

## Configuration Options

```java
public class AIDecompilerSettings extends DecompilerSettings {
    
    // Renaming safety level
    private RenamingScope renamingScope = RenamingScope.LOCAL_VARIABLES_ONLY;
    
    // Output options
    private boolean generateNameMapping = true;
    private boolean addDocumentationComments = true;
    private boolean generateWrapperClasses = false;
    private boolean preserveOriginalNames = true;
    
    // Special handling
    private boolean detectReflection = true;
    private boolean detectSerialization = true;
    private boolean detectJNI = true;
    
    // Whitelist/Blacklist
    private Set<String> neverRenameClasses = new HashSet<>();
    private Set<String> alwaysSafeToRename = new HashSet<>();
}
```

## Usage Examples

### Example 1: Safe Local-Only Renaming

```bash
java -jar procyon-decompiler.jar \
    --ai-enabled \
    --ai-rename-scope LOCAL_VARIABLES_ONLY \
    --output decompiled/ \
    obfuscated.jar
```

**Result**: Only local variables renamed, all APIs preserved
```java
public class a {  // Original name preserved
    private static a b;  // Original field name preserved
    
    public static a e() {  // Original method name preserved
        a instance = b;  // Local variable renamed!
        if (instance == null) {
            instance = new a();
            b = instance;
        }
        return instance;
    }
}
```

### Example 2: Mapping + Documentation

```bash
java -jar procyon-decompiler.jar \
    --ai-enabled \
    --generate-mapping \
    --add-ai-comments \
    --output decompiled/ \
    obfuscated.jar
```

**Result**: Original names + documentation
```java
/**
 * AI Suggested Name: ConfigurationManager
 * Pattern: Singleton
 */
public class a {
    @AIName("instance")
    private static a b;
    
    /**
     * AI Suggested Name: getInstance
     * @return Singleton instance
     */
    @AIName("getInstance")
    public static a e() {
        a currentInstance = b;
        if (currentInstance == null) {
            currentInstance = new a();
            b = currentInstance;
        }
        return currentInstance;
    }
}
```

### Example 3: Wrapper Generation

```bash
java -jar procyon-decompiler.jar \
    --ai-enabled \
    --generate-wrappers \
    --wrapper-package com.decompiled.wrappers \
    --output decompiled/ \
    obfuscated.jar
```

**Result**: Original + Wrapper class
```java
// Original preserved in: com/original/a.java

// New wrapper in: com/decompiled/wrappers/ConfigurationManager.java
package com.decompiled.wrappers;

public class ConfigurationManager {
    private final a delegate = new a();
    
    public static ConfigurationManager getInstance() {
        return new ConfigurationManager(a.e());
    }
    
    // ... other wrapped methods
}
```

## Conclusion

The safest approach is:

1. **Default**: Only rename local variables (always safe)
2. **Mapping**: Generate name mappings without changing code
3. **Documentation**: Add comments/annotations with suggested names
4. **Advanced**: Generate wrapper classes for better readability

This way, we get the benefits of AI-enhanced understanding without breaking any dependencies!
