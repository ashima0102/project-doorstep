package com.azentio.doorstep.generator;

import com.azentio.doorstep.api.dto.ApplicationRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic (seeded) synthetic applications with known fraud patterns mixed into clean traffic.
 * Every PAN, phone and name here is randomly generated; none belongs to a real person.
 */
public class SyntheticApplicationGenerator {

    private static final String[] FIRST = {"Asha", "Ravi", "Meera", "Karan", "Divya", "Arjun", "Neha", "Vikram",
            "Pooja", "Sanjay", "Lata", "Rohit", "Anita", "Manoj", "Kavya", "Suresh"};
    private static final String[] LAST = {"Verma", "Iyer", "Rao", "Shah", "Nair", "Gupta", "Menon", "Das",
            "Reddy", "Joshi", "Kulkarni", "Bose"};
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final Random random;
    private final Instant start;
    private final String idPrefix;
    private final Set<String> usedPans = new HashSet<>();
    private final Set<String> usedPhones = new HashSet<>();
    private int sequence;

    public SyntheticApplicationGenerator(long seed, Instant start, String idPrefix) {
        this.random = new Random(seed);
        this.start = start;
        this.idPrefix = idPrefix;
    }

    /** Fraud patterns first-class, then clean applicants to reach {@code total}. */
    public List<ApplicationRequest> generate(int total) {
        List<ApplicationRequest> out = new ArrayList<>();
        String ringPhone = newPhone();
        for (int i = 0; i < 8; i++) {
            out.add(base().phone(ringPhone).build());                          // phone ring
        }
        String ringDevice = "dev-ring-" + idPrefix;
        for (int i = 0; i < 8; i++) {
            out.add(base().device(ringDevice).build());                        // device ring
        }
        String recycledPan = newPan();
        LocalDate recycledDob = randomDob();
        for (int i = 0; i < 5; i++) {                                          // recycled PAN, name changes
            String name = i == 0 ? "Ramesh Kumar" : "Ramesh Kumar " + LETTERS.charAt(i);
            out.add(base().pan(recycledPan).name(name).dob(recycledDob).build());
        }
        for (int i = 0; i < 30; i++) {                                         // inflated income
            out.add(base().bureauIncome(25000 + random.nextInt(10000)).declaredIncome(150000 + random.nextInt(50000)).build());
        }
        for (int i = 0; i < 15; i++) {                                         // KYC name mismatch
            Draft d = base();
            out.add(d.nameOnId(d.name + " Singh").build());
        }
        for (int i = 0; i < 15; i++) {                                         // thin file, high amount
            out.add(base().thinFile().amount(400000 + random.nextInt(400000)).build());
        }
        while (out.size() < total) {
            out.add(base().build());                                           // clean applicants
        }
        return out;
    }

    private Draft base() {
        Draft d = new Draft();
        d.applicationId = "%s-%05d".formatted(idPrefix, ++sequence);
        d.name = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
        d.nameOnId = d.name;
        d.dob = randomDob();
        d.pan = newPan();
        d.phone = newPhone();
        d.deviceId = "dev-%s-%05d".formatted(idPrefix, sequence);
        d.amount = 50000 + random.nextInt(200000);
        d.bureauIncome = 40000 + random.nextInt(60000);
        d.declaredIncome = (int) (d.bureauIncome * (0.9 + random.nextDouble() * 0.4));
        d.bureauScore = 650 + random.nextInt(200);
        d.historyMonths = 12 + random.nextInt(120);
        d.submittedAt = start.plus(Duration.ofSeconds(sequence));
        return d;
    }

    private LocalDate randomDob() {
        return LocalDate.of(1965 + random.nextInt(37), 1 + random.nextInt(12), 1 + random.nextInt(28));
    }

    private String newPan() {
        String pan;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(LETTERS.charAt(random.nextInt(26)));
            sb.append("%04d".formatted(random.nextInt(10000)));
            sb.append(LETTERS.charAt(random.nextInt(26)));
            pan = sb.toString();
        } while (!usedPans.add(pan));
        return pan;
    }

    private String newPhone() {
        String phone;
        do {
            phone = (6 + random.nextInt(4)) + "%09d".formatted(random.nextInt(1_000_000_000));
        } while (!usedPhones.add(phone));
        return phone;
    }

    private static final class Draft {
        String applicationId, name, nameOnId, pan, phone, deviceId;
        LocalDate dob;
        int amount, bureauIncome, declaredIncome, historyMonths;
        Integer bureauScore;
        Instant submittedAt;

        Draft phone(String v) { phone = v; return this; }
        Draft device(String v) { deviceId = v; return this; }
        Draft pan(String v) { pan = v; return this; }
        Draft name(String v) { name = v; nameOnId = v; return this; }
        Draft dob(LocalDate v) { dob = v; return this; }
        Draft nameOnId(String v) { nameOnId = v; return this; }
        Draft amount(int v) { amount = v; return this; }
        Draft bureauIncome(int v) { bureauIncome = v; return this; }
        Draft declaredIncome(int v) { declaredIncome = v; return this; }
        Draft thinFile() { bureauScore = null; historyMonths = 2; return this; }

        ApplicationRequest build() {
            return new ApplicationRequest(applicationId, name, dob, pan, phone, deviceId,
                    BigDecimal.valueOf(amount), BigDecimal.valueOf(declaredIncome),
                    new ApplicationRequest.Kyc(nameOnId, dob, true),
                    new ApplicationRequest.Bureau(bureauScore, historyMonths, BigDecimal.valueOf(bureauIncome)),
                    submittedAt);
        }
    }
}
