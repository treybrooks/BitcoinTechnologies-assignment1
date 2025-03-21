package CryptoTech.ScroogeCoin;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


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

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> validTxs = new ArrayList<>();
        
        // Keep track of which UTXOs have been spent in this batch
        HashSet<UTXO> currentlySpentUTXOs = new HashSet<>();
        
        // Process all transactions in multiple passes
        boolean changesMade;
        do {
            changesMade = false;
            
            for (Transaction tx : possibleTxs) {
                // Skip transactions we've already processed
                if (validTxs.contains(tx)) continue;
                
                boolean isValid = true;
                HashSet<UTXO> txSpends = new HashSet<>();
                
                // Check each input to see if it's still available
                for (Transaction.Input input : tx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    
                    // If UTXO is not in pool or already spent in this batch, tx is invalid
                    if (!uPool.contains(utxo) || currentlySpentUTXOs.contains(utxo)) {
                        isValid = false;
                        break;
                    }
                    
                    txSpends.add(utxo);
                }
                
                // If transaction is valid, add it to our results
                if (isValid && isValidTx(tx)) {
                    validTxs.add(tx);
                    
                    // Mark these UTXOs as spent for this processing batch
                    currentlySpentUTXOs.addAll(txSpends);
                    
                    // Remove spent UTXOs from the pool
                    for (UTXO utxo : txSpends) {
                        uPool.removeUTXO(utxo);
                    }
                    
                    // Add new UTXOs for this transaction's outputs
                    byte[] txHash = tx.getHash();
                    for (int i = 0; i < tx.numOutputs(); i++) {
                        UTXO utxo = new UTXO(txHash, i);
                        uPool.addUTXO(utxo, tx.getOutput(i));
                    }
                    
                    changesMade = true;
                }
            }
        } while (changesMade); // Keep processing until no more valid transactions are found
        
        return validTxs.toArray(new Transaction[validTxs.size()]);
    }
}
