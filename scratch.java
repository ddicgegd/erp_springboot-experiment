import java.util.Date;
import java.util.concurrent.TimeUnit;
public class scratch {
    public static void main(String[] args) {
        long accessTokenExpirationMinutes = 60;
        long accessTokenExpiryMs = TimeUnit.MINUTES.toMillis(accessTokenExpirationMinutes);
        Date issueDate = new Date(System.currentTimeMillis());
        Date expiryDate = new Date(System.currentTimeMillis() + accessTokenExpiryMs);
        System.out.println("Issued: " + issueDate);
        System.out.println("Expiry: " + expiryDate);
        System.out.println("Diff (ms): " + (expiryDate.getTime() - issueDate.getTime()));
    }
}
