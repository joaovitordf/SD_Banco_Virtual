package Storage.Entities.Conta;

import java.io.Serializable;
import java.math.BigDecimal;

public class Conta implements Serializable {
    
    private int id;
    private String numeroConta;
    private BigDecimal saldo;
    private String nome;
    private String senha;
    

    public Conta(int id, String nome, String senha, String numeroConta){
        this.id = id;
        this.nome = nome;
        this.senha = senha;
        this.numeroConta = numeroConta;
        saldo = BigDecimal.valueOf(1000);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public void setNumeroConta(String numeroConta) {
        this.numeroConta = numeroConta;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    
}
