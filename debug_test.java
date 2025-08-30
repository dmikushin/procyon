class DebugTest {
    public static void test(String[] args) throws Throwable {
        String s = null;
        String s2 = null;

        try {
            try {
                s2 = args[0];

                if (args == null) {
                    throw (Exception)(Object)args;
                }

                s = args[1];
            }
            catch (final ArrayIndexOutOfBoundsException e) {
            }
        }
        catch (final Throwable t) {
            System.out.println(t instanceof NullPointerException);
            throw t;
        }

        System.out.println(s2);
        System.out.println(s);
    }
}