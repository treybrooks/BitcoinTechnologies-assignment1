package ScroogeCoin;
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
        byte[] txHash = tx.getHash();
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();

        //1 all outputs claimed by {@code tx} are in the current UTXO pool
        // for(int i = 0; i < tx.numOutputs(); i++) {
        //     if(!this.uPool.contains(new UTXO(txHash, i))) {
        //         return false;
        //     }
        // }
        for(Transaction.Input in : inputs) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if(!this.uPool.contains(utxo)) {
                return false;
            }
        }

        //2 the signatures on each input of {@code tx} are valid
        for(Transaction.Input in : inputs) {
            UTXO prevUXTO = new UTXO(in.prevTxHash, in.outputIndex);
            if(!this.uPool.contains(prevUXTO)) {
                return false;
            }

            Transaction.Output spentOutput = uPool.getTxOutput(prevUXTO);
            PublicKey pk = spentOutput.address;

            byte[] msg = tx.getRawDataToSign(in.outputIndex);
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
        for (Transaction.Input in : inputs) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if (!seenUTXOs.add(utxo)) {
                return false;
            }
        }

        //4 all of {@code tx}s output values are non-negative, and
        for(Transaction.Output out : outputs) {
            if(out.value < 0) {
                return false;
            }
        }

        //5 the sum of {@code tx}s input values is greater than or equal to the sum of its output
        //      values; and false otherwise.
        double sumIn = 0;
        double sumOut = 0;
        double spentOutValue = 0;

        for(Transaction.Input in : inputs) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            spentOutValue = uPool.getTxOutput(utxo).value;
            sumIn += spentOutValue;
        }
        for(Transaction.Output out : outputs) {
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
        ArrayList<Transaction> valid = new ArrayList<>();
        for (Transaction transaction : possibleTxs)  if (isValidTx(transaction)) valid.add(transaction);
        return valid.toArray( new Transaction[valid.size()]);
    }

}
