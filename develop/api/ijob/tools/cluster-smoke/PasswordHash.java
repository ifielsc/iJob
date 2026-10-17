import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHash {
    private PasswordHash() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one password argument");
        }
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
