import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NxtSolver {

    final String chars;
    private Map<Long, Nxt.Block> blockMap;
    private final Map<Long, Nxt.Transaction> transactionMap;
    private Map<Long, List<Nxt.Transaction>> accountMap = new HashMap<>();
    private Map<Long, List<Nxt.Block>> blockGenerationMap = new HashMap<>();

    public NxtSolver(final Path dataFolder) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Nxt.Transaction.loadTransactions(dataFolder.toString() + File.separator + "transactions.nxt");
                } catch (FileNotFoundException e) {
                    throw new IllegalStateException(e);
                }

            }
        });
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Nxt.Block.loadBlocks(dataFolder.toString() + File.separator + "blocks.nxt");
                } catch (FileNotFoundException e) {
                    throw new IllegalStateException(e);
                }

            }
        });

        // This will make the executor accept no new threads
        // and finish all existing threads in the queue
        executor.shutdown();
        // Wait until all threads are finish
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        transactionMap = Nxt.transactions;
        blockMap = Nxt.blocks;
        System.out.printf("Finished loading %d transactions and %d blocks", transactionMap.size(), blockMap.size());

        mapAccounts();
        mapBlockRewards();
        StringBuilder sb = new StringBuilder();
        for (int i=32; i<128; i++) {
            sb.append((char)i);
        }
        chars = sb.toString();
    }

    public static void main(String[] args) {
        String path = args[0];
        String wildcard = args[1];
        if (wildcard.startsWith("'") && wildcard.endsWith("'")) {
            wildcard = wildcard.substring(1, wildcard.length() - 1);
        }
        String initialGuess = "";
        if (args.length > 2) {
            initialGuess = args[2];
            if (initialGuess.startsWith("'") && initialGuess.endsWith("'")) {
                initialGuess = initialGuess.substring(1, initialGuess.length() - 1);
            }
        }
        NxtSolver solver = new NxtSolver(Paths.get(path));
        solver.solve(wildcard, initialGuess);
    }

    private void mapBlockRewards() {
        for (Nxt.Block block : blockMap.values()) {
            long generatorAccount = Nxt.Account.getId(block.generatorPublicKey);
            List<Nxt.Block> generatedBlocks = blockGenerationMap.get(generatorAccount);
            if (generatedBlocks == null) {
                generatedBlocks = new ArrayList<>();
                generatedBlocks.add(block);
                blockGenerationMap.put(generatorAccount, generatedBlocks);
            } else {
                generatedBlocks.add(block);
            }
        }
    }

    private void mapAccounts() {
        for (Nxt.Transaction transaction : transactionMap.values()) {
            List<Nxt.Transaction> recipientTransactions = accountMap.get(transaction.recipient);
            if (recipientTransactions == null) {
                recipientTransactions = new ArrayList<>();
                recipientTransactions.add(transaction);
                accountMap.put(transaction.recipient, recipientTransactions);
            } else {
                recipientTransactions.add(transaction);
            }
            long sender = Nxt.Account.getId(transaction.senderPublicKey);
            List<Nxt.Transaction> senderTransactions = accountMap.get(sender);
            if (senderTransactions == null) {
                senderTransactions = new ArrayList<>();
                senderTransactions.add(transaction);
                accountMap.put(sender, senderTransactions);
            } else {
                senderTransactions.add(transaction);
            }
        }
        System.out.println("Recipient map size with empty accounts: " + accountMap.size());
        for(Iterator<Map.Entry<Long, List<Nxt.Transaction>>> it = accountMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, List<Nxt.Transaction>> entry = it.next();
            if (getAccountBalance(entry.getKey()) == 0) {
                it.remove();
            }
        }
        System.out.println("Recipient map size without empty accounts: " + accountMap.size());
    }

    private int getAccountBalance(Long id) {
        List<Nxt.Transaction> recipientTransactions = accountMap.get(id);
        int balance = 0;
        for (Nxt.Transaction transaction : recipientTransactions) {
            long sender = Nxt.Account.getId(transaction.senderPublicKey);
            if (transaction.recipient == id) {
                balance += transaction.amount;
            }
            if (sender == id) {
                balance -= transaction.amount;
                balance -= transaction.fee;
                if (sender == transaction.recipient) {
                    balance += 1;
                }
            }
        }
        balance += getAccountFeeReward(id);
        return balance;
    }

    private int getAccountFeeReward(Long id) {
        List<Nxt.Block> generatedBlocks = blockGenerationMap.get(id);
        if (generatedBlocks == null) {
            return 0;
        }
        int fee = 0;
        for (Nxt.Block block : generatedBlocks) {
            fee += block.totalFee;
        }
        return fee;
    }

    String solve(String wildcard, String initialGuess) {
        int[] offsets = getOffsets(wildcard, initialGuess);
        int counter = getCounter(offsets);

        StringBuilder sb = new StringBuilder();
        while (true) {
            String secretPhrase = getSecretPhrase(wildcard, counter, offsets, sb);
            counter ++;

            String id = initializeKeyPair(secretPhrase).toString();
            long guess = new BigInteger(id).longValue();
            if (counter % 100000 == 0) {
                System.out.printf("Progress: secretPhrase '%s' counter %d guess %d\n", secretPhrase, counter, guess);
            }
            if (Math.abs(guess) <= 100000) {
                System.out.println("Small guess " + guess);
            }
            if (accountMap.get(guess) != null) {
                System.out.printf("bingo secretPhrase %s id %s recipient %d counter %d balance %d\n",
                        secretPhrase, id, guess, counter, getAccountBalance(guess));
                return secretPhrase;
            }
        }
    }

    int getCounter(int[] offsets) {
        int m = 1;
        int counter = 0;
        for (int offset : offsets) {
            if (offset == -1) {
                continue;
            }
            counter += m * offset;
            m *= chars.length();
        }
        return counter;
    }

    String getSecretPhrase(String wildcard, int n, int[] offsets, StringBuilder sb) {
        for (int i=0; i < offsets.length; i++) {
            if (offsets[i] == -1) {
                continue;
            }
            int r = n % chars.length();
            offsets[i] = r;
            n -= r;
            if (n==0) {
                break;
            }
            n /= chars.length();
        }
        sb.setLength(0);
        for (int i=0; i < offsets.length; i++) {
            if (offsets[i] == -1) {
                sb.append(wildcard.charAt(i));
            } else {
                sb.append(chars.charAt(offsets[i]));
            }
        }
        return sb.toString();
    }

    int[] getOffsets(String wildcard, String initialGuess) {
        int[] offests = new int[wildcard.length()];
        Arrays.fill(offests, -1);
        int wildCardChars = 0;
        for (int i=0; i<offests.length; i++) {
            if (wildcard.charAt(i) == '*') {
                int offset = 0;
                if (wildCardChars < initialGuess.length()) {
                    offset = chars.indexOf(initialGuess.charAt(wildCardChars));
                    if (offset == -1) {
                        offset = 0;
                    }
                }
                offests[i] = offset;
                wildCardChars ++;
            }
        }
        return offests;
    }

    BigInteger initializeKeyPair(String secretPhrase) {
        byte[] publicKeyHash;
        try {
            byte[] publicKey = Nxt.Crypto.getPublicKey(secretPhrase);
            publicKeyHash = MessageDigest.getInstance("SHA-256").digest(publicKey);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new BigInteger(1, new byte[]{publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0]});
    }
}
