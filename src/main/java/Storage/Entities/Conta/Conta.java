package Storage.Entities.Conta;

import Model.Transferencia;

import java.io.Serializable;
import java.math.BigDecimal;

public class Conta implements Serializable {
    
    private int id;
    private String cpf;
    private BigDecimal saldo;
    private String nome;
    private String senha;
    private Transferencia transferencia;

    public Conta(int id, BigDecimal saldo) {
        this.id = id;
        this.saldo = saldo;
    }

    public Conta(int id, Transferencia transferencia){
        this.id = id;
        this.transferencia = transferencia;
    }

    public Conta(String nome, String senha){
        this.nome = nome;
        this.senha = senha;
    }

    public Conta(int id, String nome, BigDecimal valor) {
        this.id = id;
        this.nome = nome;
        this.saldo = valor;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    
}
