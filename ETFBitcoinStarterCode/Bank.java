import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Bank {

    // Constants and Global Variables
    private static final String WALLET_FILE_NAME = "bitcoin-wallet";
    private static final String WALLET_FILE_NAME_2 = "receiver-wallet";
    private static final NetworkParameters params = TestNet3Params.get();
    private static final long RECENT_PERIOD = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    private static List<Transaction> recentTransactions = new ArrayList<>();
    private static WalletAppKit walletAppKit = null;

    static {
        // Assuming SLF4J is bound to logback
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.ERROR);
    }

    public static void main(String[] args) throws Exception {
        // Wallet setup
        Wallet wallet = checkOrCreateWallet(params); 
        Wallet personalWallet = checkOrCreateWallet2(params);
        // The address of the second wallet you want to send Bitcoin to
        
        //Address recipientAddress = walletAppKit.wallet().freshReceiveAddress();

        // Event listener for transactions
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                long currentTime = System.currentTimeMillis();
                long transactionTime = tx.getUpdateTime().getTime();
                if (currentTime - transactionTime <= RECENT_PERIOD) {
                    recentTransactions.add(tx);
                    System.out.println("New recent transaction: " + tx.getHashAsString());
                }
            }
        });
        
        // Specify the percentage of the balance to send (e.g., 10%)
        long percentageToSend = (long)5.0;

        // Initial setup output
        printWalletAndConnectionInfo(wallet); 


        // Continuous balance check loop
        int index = 0;
        while (true) {
            index++;
            System.out.println("Wallet balance (in satoshis): " + wallet.getBalance().value);
            System.out.println("Block height: " + walletAppKit.chain().getBestChainHeight());
            System.out.println("Peers: " + walletAppKit.peerGroup().getConnectedPeers().size());

            // Optionally, clean up old transactions from the list
            long currentTime = System.currentTimeMillis();
            recentTransactions.removeIf(tx -> currentTime - tx.getUpdateTime().getTime() > RECENT_PERIOD);

            TimeUnit.SECONDS.sleep(30); // Adjust check interval as needed

            // Calculate the amount to send based on the percentage
            if(index%20 == 0){
                Coin senderBalance = wallet.getBalance();
                Coin amountToSend = senderBalance.divide(100).multiply(percentageToSend);
 
                // Create a SendRequest
                SendRequest sendRequest = SendRequest.to(personalWallet.currentReceiveAddress(), amountToSend);
 
                // Send the coins
                wallet.sendCoins(sendRequest);
 
                // Wait for the transaction to be broadcasted
                Transaction transaction = sendRequest.tx;
                System.out.println("Transaction ID: " + transaction.getHashAsString());
 
                // Optionally, you can wait for the transaction to be confirmed
                // (Note: This is a blocking call and may take some time)
        
                wallet.commitTx(transaction);
            }
        }
    }

    // Helper Functions
    private static Wallet checkOrCreateWallet(NetworkParameters params) throws IOException, UnreadableWalletException {
        walletAppKit = new WalletAppKit(params, new File("."), WALLET_FILE_NAME);
        walletAppKit.setBlockingStartup(false);
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();        
        walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.001); // Example: 0.1% false positive rate

        System.out.println("Wallet address: " + walletAppKit.wallet().currentReceiveAddress().toString());

        File walletFile = new File(WALLET_FILE_NAME + ".wallet");
        
        if (walletFile.exists()) {
            // Wallet exists, load it
            return Wallet.loadFromFile(walletFile);
        } else {
            Wallet wallet = walletAppKit.wallet();
            wallet.saveToFile(walletFile);
            throw new UnreadableWalletException("Wallet not found, created a new one");
        }
    }

    private static Wallet checkOrCreateWallet2(NetworkParameters params) throws IOException, UnreadableWalletException {
        walletAppKit = new WalletAppKit(params, new File("."), WALLET_FILE_NAME_2);
        walletAppKit.setBlockingStartup(false);
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();        
        walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.001); // Example: 0.1% false positive rate

        System.out.println("Wallet address: " + walletAppKit.wallet().currentReceiveAddress().toString());

        File walletFile = new File(WALLET_FILE_NAME_2 + ".wallet");
        
        if (walletFile.exists()) {
            // Wallet exists, load it
            return Wallet.loadFromFile(walletFile);
        } else {
            Wallet wallet = walletAppKit.wallet();
            wallet.saveToFile(walletFile);
            throw new UnreadableWalletException("Wallet not found, created a new one");
        }
    }

    private static void printWalletAndConnectionInfo(Wallet wallet) {
        System.out.println("Initial Balance: " + wallet.getBalance().toFriendlyString());
        System.out.println("Network: " + params.getId());
        System.out.println("Connected peers: " + walletAppKit.peerGroup().getConnectedPeers().size());
        System.out.println("Wallet address: " + wallet.currentReceiveAddress().toString());
        System.out.println("Block height: " + walletAppKit.chain().getBestChainHeight());
    }
}   
