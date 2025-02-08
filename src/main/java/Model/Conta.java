package Model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

public class Conta implements Serializable {
    private static int contadorId = 1; // Contador de ID global para incremento automático
    private int id;
    private String cpf;
    private BigDecimal saldo;
    private String nome;
    private String senha;

    public Conta(int id, BigDecimal saldo) {
        this.id = id;
        this.saldo = saldo;
    }

    public Conta(String nome, String senha) {
        this.id = contadorId++; // Atribui e incrementa o ID
        this.nome = nome;
        this.senha = senha;
        this.saldo = BigDecimal.valueOf(1000); // Saldo inicial padrão
    }

    public Conta(int id, String nome, BigDecimal valor) {
        this.id = id;
        this.nome = nome;
        this.saldo = valor;
    }

    public static String criptografarSenha(String senha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(senha.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao criptografar a senha", e);
        }
    }

    public static boolean verificarSenha(String senhaDigitada, String senhaArmazenada) {
        return criptografarSenha(senhaDigitada).equals(senhaArmazenada);
    }

    public static void alterarSenha(Map<String, Conta> clientes, String nome, String novaSenha) {
        if (clientes.containsKey(nome)) {
            clientes.get(nome).setSenha(novaSenha);
        }
    }

    public static void removerCliente(Map<String, Conta> clientes, String nome) {
        clientes.remove(nome);
    }

    public static boolean realizarTransferencia(Map<String, Conta> clientes, String remetente, String destinatario, BigDecimal valor) {
        if (clientes.containsKey(remetente) && clientes.containsKey(destinatario)) {
            Conta contaOrigem = clientes.get(remetente);
            Conta contaDestino = clientes.get(destinatario);

            if (contaOrigem.getSaldo().compareTo(valor) >= 0) {
                contaOrigem.setSaldo(contaOrigem.getSaldo().subtract(valor));
                contaDestino.setSaldo(contaDestino.getSaldo().add(valor));
                return true;
            } else {
                System.out.println("[ERRO] Saldo insuficiente para a transferência.");
            }
        } else {
            System.out.println("[ERRO] Uma das contas informadas não existe.");
        }
        return false;
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

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }
}
