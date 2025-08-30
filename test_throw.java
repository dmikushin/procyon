class TestThrow {
    public static void test(String[] args) throws Throwable {
        try {
            if (args == null) {
                throw (Exception)(Object)args;
            }
        } catch (Throwable t) {
            throw t;
        }
    }
}