package Storage.Entities.Conta;

import java.io.Serializable;
import java.math.BigDecimal;

public class Conta {
    
    private int id;
    private Integer numeroConta;
    private BigDecimal saldo;
    private String nome;
    private String senha;
    

    public Conta(int id, String nome, String senha, Integer numeroConta){
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

    public Integer getNumeroConta() {
        return numeroConta;
    }

    public void setNumeroConta(int numeroConta) {
        this.numeroConta = numeroConta;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    
}
