package Model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.io.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class Conta implements Serializable {
    private static int contadorId = 1; // Contador de ID global para incremento automático
    private int id;
    private String cpf;
    private BigDecimal saldo;
    private String nome;
    private String senha;
    private static final String caminhoJson = retornaDiretorio("clientes.json");

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

    public static String retornaDiretorio(String document) {
        // Obtém o diretório atual onde o programa está rodando
        String dirPath = new File("").getAbsolutePath();

        return dirPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator
                + document;
    }

    public static void salvarCadastroEmArquivo(String nome, String senha) {
        try {
            if (clienteExistente(nome)) {
                System.out.println("[SERVIDOR] Cliente já cadastrado.");
                return;
            }

            File arquivo = new File(caminhoJson);
            JSONArray clientesArray = new JSONArray();
            int maiorId = 0;

            if (arquivo.exists() && arquivo.length() > 0) {
                try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                    String content = reader.lines().reduce("", String::concat).trim();
                    if (content.startsWith("[") && content.endsWith("]")) {
                        clientesArray = new JSONArray(content);
                        for (int i = 0; i < clientesArray.length(); i++) {
                            JSONObject cliente = clientesArray.getJSONObject(i);
                            if (cliente.has("id")) {
                                maiorId = Math.max(maiorId, cliente.getInt("id"));
                            }
                        }
                    }
                }
            }

            int novoId = maiorId + 1;
            JSONObject json = new JSONObject();
            json.put("id", novoId);
            json.put("nome", nome);
            json.put("senha", senha);
            json.put("saldo", 1000);
            clientesArray.put(json);

            try (FileWriter file = new FileWriter(arquivo)) {
                file.write(clientesArray.toString(4));
            }

            System.out.println("[SERVIDOR] Cliente cadastrado.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean clienteExistente(String nome) {
        File arquivo = new File(caminhoJson);
        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                String content = reader.lines().reduce("", String::concat).trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equals(nome)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static String consultarSaldoClientePorNome(String nome) {
        return consultarCampoCliente(nome, "saldo");
    }

    public static String consultarIdClienteOrigem(String nome) {
        return consultarCampoCliente(nome, "id");
    }

    public static String consultarIdClienteDestino(String nome) {
        return consultarCampoCliente(nome, "id");
    }

    private static String consultarCampoCliente(String nome, String campo) {
        File arquivo = new File(caminhoJson);
        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                String content = reader.lines().reduce("", String::concat).trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            return String.valueOf(cliente.get(campo));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado.";
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
