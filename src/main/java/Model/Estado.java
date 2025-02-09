package Model;

import java.io.*;

public class Estado implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String clientesJson;

    public Estado() {
        this.clientesJson = lerClientesDoArquivo(obterCaminhoArquivo());
    }

    public Estado(String clientesJson) {
        this.clientesJson = clientesJson != null ? clientesJson : "[]";
    }

    private static String obterCaminhoArquivo() {
        return new File("clientes.json").getAbsolutePath(); // Evita dependência de estrutura de diretórios fixa
    }

    private String lerClientesDoArquivo(String caminho) {
        File arquivo = new File(caminho);
        if (!arquivo.exists()) {
            System.out.println("[ESTADO] Arquivo " + caminho + " não encontrado. Retornando lista vazia.");
            return "[]";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }
            return sb.toString();
        } catch (IOException e) {
            System.err.println("[ESTADO] Erro ao ler o arquivo " + caminho + ": " + e.getMessage());
            e.printStackTrace();
            return "[]";
        }
    }

    public String getClientesJson() {
        return clientesJson;
    }

    public void setClientesJson(String clientesJson) {
        this.clientesJson = clientesJson;
    }
}