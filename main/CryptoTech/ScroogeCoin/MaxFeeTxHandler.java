package CryptoTech.ScroogeCoin;
import java.security.PublicKey;
import java.util.*;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public UTXOPool uPool;
    public TxHandler(UTXOPool utxoPool) {
        this.uPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        //1 all outputs claimed by {@code tx} are in the current UTXO pool
        // for(int i = 0; i < tx.numOutputs(); i++) {
        //     if(!this.uPool.contains(new UTXO(txHash, i))) {
        //         return false;
        //     }
        // }
        for(Transaction.Input in : tx.getInputs()) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if(!this.uPool.contains(utxo)) {
                return false;
            }
        }

        //2 the signatures on each input of {@code tx} are valid
        for(int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input in = tx.getInput(i);
            UTXO prevUXTO = new UTXO(in.prevTxHash, in.outputIndex);
            if(!this.uPool.contains(prevUXTO)) {
                return false;
            }

            Transaction.Output spentOutput = uPool.getTxOutput(prevUXTO);
            PublicKey pk = spentOutput.address;

            byte[] msg = tx.getRawDataToSign(i);
            if (msg == null) return false;  // Prevent null issues before verification
            
            byte[] signature = in.signature;
            if (signature == null) return false;  // Ensure the signature is not null

            try{
                if(!Crypto.verifySignature(pk, msg, signature)) {
                    return false;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                return false;  // Return false instead of running into a NullPointerException
            }
        }

        //3 no UTXO is claimed multiple times by {@code tx},
        Set<UTXO> seenUTXOs = new HashSet<>();
        // for (UTXO utxo : uPool.getAllUTXO()) {
        for (Transaction.Input in : tx.getInputs()) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if (!seenUTXOs.add(utxo)) {
                return false;
            }
        }

        //4 all of {@code tx}s output values are non-negative, and
        for(Transaction.Output out : tx.getOutputs()) {
            if(out.value < 0) {
                return false;
            }
        }

        //5 the sum of {@code tx}s input values is greater than or equal to the sum of its output
        //      values; and false otherwise.
        double sumIn = 0;
        double sumOut = 0;
        double spentOutValue = 0;

        for(Transaction.Input in : tx.getInputs()) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            spentOutValue = uPool.getTxOutput(utxo).value;
            sumIn += spentOutValue;
        }
        for(Transaction.Output out : tx.getOutputs()) {
            sumOut += out.value;
        }
        if(sumIn < sumOut) {
            return false;
        }

        // if none of that failed ->
        return true;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // Create a list to store all valid transactions
        List<Transaction> validTxs = new ArrayList<>();
        
        // Create a map to store each transaction's fee
        Map<Transaction, Double> txFees = new HashMap<>();
        
        // Create a copy of the UTXO pool to work with
        UTXOPool poolCopy = new UTXOPool(this.uPool);
        
        // First, calculate the fee for each transaction and check initial validity
        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                double inputSum = 0;
                double outputSum = 0;
                
                // Calculate total input value
                for (Transaction.Input input : tx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    if (poolCopy.contains(utxo)) {
                        inputSum += poolCopy.getTxOutput(utxo).value;
                    }
                }
                
                // Calculate total output value
                for (Transaction.Output output : tx.getOutputs()) {
                    outputSum += output.value;
                }
                
                double fee = inputSum - outputSum;
                txFees.put(tx, fee);
            }
        }
        
        // Sort transactions by fee (highest fee first)
        List<Transaction> sortedTxs = new ArrayList<>(txFees.keySet());
        sortedTxs.sort((tx1, tx2) -> Double.compare(txFees.get(tx2), txFees.get(tx1)));
        
        // Create a map to track which UTXOs are consumed
        Set<UTXO> usedUTXOs = new HashSet<>();
        
        // Process transactions in order of highest fee
        for (Transaction tx : sortedTxs) {
            boolean allInputsAvailable = true;
            Set<UTXO> txUTXOs = new HashSet<>();
            
            // Check if all inputs are available
            for (Transaction.Input input : tx.getInputs()) {
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                if (usedUTXOs.contains(utxo) || !poolCopy.contains(utxo)) {
                    allInputsAvailable = false;
                    break;
                }
                txUTXOs.add(utxo);
            }
            
            // If transaction can be processed, add it to valid transactions
            if (allInputsAvailable) {
                validTxs.add(tx);
                
                // Mark UTXOs as used
                usedUTXOs.addAll(txUTXOs);
                
                // Update the actual UTXO pool
                for (UTXO utxo : txUTXOs) {
                    uPool.removeUTXO(utxo);
                }
                
                // Add new UTXOs for the outputs
                byte[] txHash = tx.getHash();
                for (int i = 0; i < tx.numOutputs(); i++) {
                    UTXO utxo = new UTXO(txHash, i);
                    uPool.addUTXO(utxo, tx.getOutput(i));
                    poolCopy.addUTXO(utxo, tx.getOutput(i));
                }
            }
        }
        
        return validTxs.toArray(new Transaction[validTxs.size()]);
    }
}

