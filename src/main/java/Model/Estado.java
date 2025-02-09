package Model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import Controller.Controller;

public class Estado implements java.io.Serializable {
    private String clientesJson;  // Armazena como String JSON
    private int versao;

    public Estado(Controller cluster) {
        try {
            this.clientesJson = gerarJsonClientes(cluster.getClientes());  // Converte o mapa de clientes para JSON
            this.versao = consultarVersao();
        } catch (Exception e) {
            this.versao = 1;
            this.clientesJson = "[]"; // JSON vazio
        }
    }

    private String gerarJsonClientes(Map<String, Conta> clientes) {
        JSONArray jsonArray = new JSONArray();

        for (Map.Entry<String, Conta> entry : clientes.entrySet()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("nome", entry.getKey());
            jsonObject.put("senha", entry.getValue().getSenha());
            jsonObject.put("id", entry.getValue().getId());
            jsonObject.put("saldo", entry.getValue().getSaldo());

            jsonArray.put(jsonObject);
        }

        return jsonArray.toString(4); // Formatação bonita
    }

    public static int consultarVersao() {
        int versao;
        try {
            versao = Integer.parseInt(new String(Files.readAllBytes(Paths.get("versao.txt"))));
        } catch (Exception e) {
            versao = 1;
        }
        return versao;
    }

    public static void atualizarVersao() throws Exception {
        int versao = consultarVersao();
        versao++;
        Files.write(Paths.get("versao.txt"), String.valueOf(versao).getBytes());
    }

    public String getClientesJson() {
        return clientesJson;
    }

    public void setClientesJson(String clientesJson) {
        this.clientesJson = clientesJson;
    }

    public int getVersao() {
        return versao;
    }
}
