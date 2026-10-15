package io.projectir.mobile;

public final class LocalCommandRouterTest {
    private static int passed = 0;
    private static void check(String input, LocalCommandRouter.Kind expected) {
        LocalCommandRouter.Kind actual = LocalCommandRouter.parse(input);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
        passed++;
    }
    public static void main(String[] args) {
        check("تنظیمات را باز کن", LocalCommandRouter.Kind.OPEN_SETTINGS);
        check("تنظیمات رو باز کن", LocalCommandRouter.Kind.OPEN_SETTINGS);
        check("تنظيمات را باز كن", LocalCommandRouter.Kind.OPEN_SETTINGS);
        check("تنظیمات را باز کن و وارد Connections شو", LocalCommandRouter.Kind.OPEN_CONNECTIONS);
        check("وارد اتصالات شو", LocalCommandRouter.Kind.OPEN_CONNECTIONS);
        check("open settings connections", LocalCommandRouter.Kind.OPEN_CONNECTIONS);
        check("ماشین حساب رو باز کن", LocalCommandRouter.Kind.OPEN_CALCULATOR);
        check("ساعت را باز کن", LocalCommandRouter.Kind.OPEN_CLOCK);
        check("نقشه را باز کن", LocalCommandRouter.Kind.OPEN_MAPS);
        check("وضعیت گوشی", LocalCommandRouter.Kind.DEVICE_STATUS);
        check("device status", LocalCommandRouter.Kind.DEVICE_STATUS);
        check("پنج ثانیه صبر کن", LocalCommandRouter.Kind.WAIT_FIVE_SECONDS);
        check("wait five seconds", LocalCommandRouter.Kind.WAIT_FIVE_SECONDS);
        check("", LocalCommandRouter.Kind.UNSUPPORTED);
        check("حذف همه پیام ها", LocalCommandRouter.Kind.UNSUPPORTED);
        check("open settings; rm -rf /", LocalCommandRouter.Kind.UNSUPPORTED);
        check("send a message to Ali", LocalCommandRouter.Kind.UNSUPPORTED);
        System.out.println("LOCAL_ROUTER_TESTS_PASS=" + passed);
    }
}
