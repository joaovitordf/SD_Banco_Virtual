package Controller;

import Model.BancoGatewayInterface;
import Model.Conta;
import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.math.BigDecimal;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class Controller implements Receiver, RequestHandler, BancoGatewayInterface {
    private JChannel channel;
    private MessageDispatcher despachante;
    private HashMap<Integer, Conta> contas = new HashMap<>();
    final List<String> state = new LinkedList<String>();
    private int idConta = 1;
    private Map<String, Conta> clientes = new HashMap<>(); // Mapa para armazenar clientes
    private String caminhoJson = retornaDiretorio("clientes.json");
    private boolean isCoordenador = false;

    public static void main(String[] args) {
        try {
            new Controller().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String retornaDiretorio(String document) {
        // Obtém o diretório atual onde o programa está rodando
        String dirPath = new File("").getAbsolutePath();

        return dirPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator
                + document;
    }

    private void start() throws Exception {
        // Define um hostname acessível para comunicação entre servidores
        String ipLocal = getLocalIPAddress();
        System.out.println(ipLocal);
        if (ipLocal != null) {
            System.setProperty("java.rmi.server.hostname", ipLocal);
            System.setProperty("jgroups.bind_addr", ipLocal);
        } else {
            System.out.println("Erro: Não foi possível encontrar um IP válido na rede 192.168.x.x.");
        }

        // Conectar ao canal JGroups
        channel = new JChannel(retornaDiretorio("cast.xml"));
        channel.setReceiver(this);
        channel.connect("BancoCluster");
        channel.getState(null, 10000);
        despachante = new MessageDispatcher(this.channel, this);

        // Registrar o servidor no RMI Registry
        configurarRMI();
        System.out.println("[SERVIDOR] Servidor iniciado com sucesso.");

        eventLoop();
        channel.close();
    }

    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }

    private void eventLoop() throws Exception {
        System.out.println("[DEBUG] Controller ativo. Digite 'sair' para encerrar.");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                Thread.sleep(1000);
                String line = scanner.nextLine().toLowerCase();
                if ("sair".equals(line)) {
                    break;
                }
            }
        }
    }

    @Override
    public boolean realizarLogin(String nome, String senha) throws RemoteException {
        return verificarCredenciais(nome, senha);
    }

    @Override
    public boolean cadastrarCliente(String nome, String senha) throws RemoteException {
        if (clientes.containsKey(nome)) {
            return false; // Cliente já existe
        }
        Conta conta = new Conta(nome, senha);
        clientes.put(nome, conta);
        salvarCadastroEmArquivo(nome, senha);

        if (isCoordenador) { // O coordenador propaga, mas não processa a própria propagação
            System.out.println("[SERVIDOR] Propagando novo cliente: " + nome);
            propagarAtualizacao("CADASTRO", nome, senha);
        }

        return true;
    }

    @Override
    public String consultarSaldo(String nome) throws RemoteException {
        return consultarSaldoClientePorNome(nome);
    }

    @Override
    public BigDecimal somarSaldos() throws RemoteException {
        return new BigDecimal(somarSaldosClientes());
    }

    @Override
    public boolean removerCliente(String nome) throws RemoteException {
        String resultado = removerClienteDoArquivo(nome);
        boolean sucesso = resultado.toLowerCase().contains("removido com sucesso");

        if (sucesso) {
            System.out.println("[SERVIDOR] Cliente sendo removido: " + nome);

            if (isCoordenador) { // Apenas o coordenador propaga a alteração
                System.out.println("[SERVIDOR] Propagando remoção do cliente: " + nome);
                propagarAtualizacao("REMOVER", nome, "");
            }
        } else {
            System.out.println("[SERVIDOR] Falha ao remover o cliente: " + nome + ". Retorno do método: " + resultado);
        }

        return sucesso;
    }

    @Override
    public boolean alterarSenha(String nome, String novaSenha) throws RemoteException {
        String resultado = alterarSenhaCliente(nome, novaSenha);
        boolean sucesso = resultado.toLowerCase().contains("alterada com sucesso");

        if (sucesso) {
            System.out.println("[SERVIDOR] Senha alterada para o cliente: " + nome);

            if (isCoordenador) { // Apenas o coordenador propaga a alteração
                System.out.println("[SERVIDOR] Propagando alteração de senha do cliente: " + nome);
                propagarAtualizacao("ALTERAR", nome, novaSenha);
            }
        } else {
            System.out.println("[SERVIDOR] Falha ao alterar senha para o cliente: " + nome + ". Retorno do método: " + resultado);
        }

        return sucesso;
    }


    @Override
    public boolean realizarTransferencia(String remetente, String destinatario, BigDecimal valor)
            throws RemoteException {
        String origem = consultarIdClienteOrigem(remetente);
        int idOrigem = extrairIdConta(origem);
        String destino = consultarIdClienteOrigem(destinatario);
        int idDestino = extrairIdConta(destino);

        if (idOrigem == -1 || idDestino == -1) {
            return false; // Conta de origem ou destino não encontrada
        }

        BigDecimal saldoOrigem = consultarSaldoClientePorId(idOrigem);
        BigDecimal saldoDestino = consultarSaldoClientePorId(idDestino);

        if (saldoOrigem.compareTo(valor) < 0) {
            System.out.println("[SERVIDOR] Saldo insuficiente para transferência.");
            return false; // Saldo insuficiente
        }

        Conta contaOrigem = new Conta(idOrigem, saldoOrigem.subtract(valor));
        Conta contaDestino = new Conta(idDestino, saldoDestino.add(valor));

        atualizarSaldoNoArquivo(contaOrigem);
        atualizarSaldoNoArquivo(contaDestino);

        System.out.println("[SERVIDOR] Transferência concluída.");

        if (isCoordenador) {
            System.out.println("[SERVIDOR] Propagando transferência de " + valor + " de " + idOrigem + " para " + idDestino);
            propagarAtualizacao("TRANSFERIR", idOrigem + "", idDestino + ":" + valor);
        }

        return true;
    }



    private int extrairIdConta(String resposta) {
        try {
            // Acha o número no final da string usando regex
            String numero = resposta.replaceAll("[^0-9]", "");
            return Integer.parseInt(numero);
        } catch (NumberFormatException e) {
            System.out.println("[ERRO] Não foi possível extrair o ID da conta: " + resposta);
            return -1; // Retorna -1 se houver erro
        }
    }

    public void receive(Message msg) {
        try {
            Object object = msg.getObject(); // Objeto recebido (Conta)

            if (object instanceof Conta) {
                // Desserializando o objeto Conta enviado pelo cliente
                Conta conta = (Conta) object;
                System.out.println("[SERVIDOR] Conta recebida: " + conta + ", Nome: " + conta.getNome());

                // Armazenando a conta no mapa de clientes
                clientes.put(conta.getNome(), conta); // Associando a conta pelo numero da conta ou outro identificador

                salvarCadastroEmArquivo(conta.getNome(), conta.getSenha());
            } else if (object instanceof String mensagem) {

                if (!isCoordenador) {
                    String[] partes = mensagem.split(":");
                    String operacao = partes[0];
                    String nomeCliente = partes.length > 1 ? partes[1].trim() : "";
                    String valor = partes.length > 2 ? partes[2].trim() : "";

                    switch (operacao) {
                        case "LOGIN":
                            boolean loginValido = verificarCredenciais(nomeCliente, valor);
                            Message respostaLogin = new ObjectMessage(msg.getSrc(), loginValido);
                            channel.send(respostaLogin);
                            break;

                        case "CONSULTAR_ID_ORIGEM":
                            String respostaIdOrigem = consultarIdClienteOrigem(nomeCliente);
                            Message respostaOrigem = new ObjectMessage(msg.getSrc(), respostaIdOrigem);
                            channel.send(respostaOrigem);
                            break;

                        case "CONSULTAR_ID_DESTINO":
                            String respostaIdDestino = consultarIdClienteDestino(nomeCliente);
                            Message respostaDestino = new ObjectMessage(msg.getSrc(), respostaIdDestino);
                            channel.send(respostaDestino);
                            break;

                        case "CONSULTAR_SALDO":
                            String respostaSaldo = consultarSaldoClientePorNome(nomeCliente);
                            Message respostaConsulta = new ObjectMessage(msg.getSrc(), respostaSaldo);
                            channel.send(respostaConsulta);
                            break;

                        case "SOMAR_SALDOS":
                            System.out.println("[SERVIDOR] Solicitação de soma dos saldos recebida.");
                            String resultadoSoma = somarSaldosClientes();
                            Message respostaSoma = new ObjectMessage(msg.getSrc(), resultadoSoma);
                            channel.send(respostaSoma);
                            break;

                        case "CADASTRO":
                            if (!clienteExistente(nomeCliente)) {
                                salvarCadastroEmArquivo(nomeCliente, valor);
                                System.out.println("[SERVIDOR] Cliente " + nomeCliente + " cadastrado a partir da propagação.");
                            } else {
                                System.out.println("[SERVIDOR] Cliente " + nomeCliente + " já cadastrado. Ignorando propagação.");
                            }
                            break;

                        case "REMOVER":
                            if (clienteExistente(nomeCliente)) {
                                String resultadoRemocao = removerClienteDoArquivo(nomeCliente);
                                System.out.println(resultadoRemocao);
                            } else {
                                System.out.println("[SERVIDOR] Cliente " + nomeCliente + " não encontrado. Ignorando propagação.");
                            }
                            break;

                        case "ALTERAR":
                            System.out.println("[SERVIDOR] Recebida solicitação para alterar senha do cliente: " + nomeCliente);
                            if (clienteExistente(nomeCliente)) {
                                String resultadoAlteracao = alterarSenhaCliente(nomeCliente, valor);

                                // Salvar no JSON local para garantir persistência
                                salvarCadastroEmArquivo(nomeCliente, valor);
                            } else {
                                System.out.println("[SERVIDOR] Cliente " + nomeCliente + " não encontrado. Ignorando propagação.");
                            }
                            break;

                        case "TRANSFERIR":
                            if (partes.length == 4) {
                                try {
                                    int idOrigem = Integer.parseInt(partes[1]);
                                    int idDestino = Integer.parseInt(partes[2]);
                                    BigDecimal valorTransferencia = new BigDecimal(partes[3]);

                                    System.out.println("[SERVIDOR] Recebida solicitação para transferir " + valorTransferencia
                                            + " de " + idOrigem + " para " + idDestino);

                                    // Verificar se a transferência já foi processada
                                    BigDecimal saldoAtual = consultarSaldoClientePorId(idOrigem);
                                    if (saldoAtual.compareTo(valorTransferencia) < 0) {
                                        System.out.println("[SERVIDOR] Transferência já processada ou saldo insuficiente. Ignorando.");
                                        break;
                                    }

                                    // Criar contas e atualizar saldos
                                    Conta contaOrigem = new Conta(idOrigem, saldoAtual.subtract(valorTransferencia));
                                    Conta contaDestino = new Conta(idDestino, consultarSaldoClientePorId(idDestino).add(valorTransferencia));

                                    atualizarSaldoNoArquivo(contaOrigem);
                                    atualizarSaldoNoArquivo(contaDestino);

                                    System.out.println("[SERVIDOR] Transferência processada com sucesso.");

                                } catch (NumberFormatException e) {
                                    System.out.println("[SERVIDOR] Erro ao converter dados da transferência: " + e.getMessage());
                                }
                            } else {
                                System.out.println("[SERVIDOR] Mensagem de transferência mal formatada: " + mensagem);
                            }
                            break;

                        default:
                            System.out.println("[SERVIDOR] Mensagem desconhecida: " + mensagem);
                    }
                }
            } else {
                // Envia mensagem de erro para o cliente caso a mensagem seja inválida
                Message respostaInvalid = new ObjectMessage(msg.getSrc(), "Mensagem inválida.");
                channel.send(respostaInvalid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        synchronized (clientes) {
            File arquivo = new File(caminhoJson);
            if (arquivo.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                    StringBuilder sb = new StringBuilder();
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        sb.append(linha);
                    }
                    Util.objectToStream(sb.toString(), new DataOutputStream(output));
                    System.out.println("[SERVIDOR] Estado enviado para um novo nó do cluster.");
                }
            } else {
                System.out.println("[SERVIDOR] Arquivo JSON não encontrado. Enviando estado vazio.");
                Util.objectToStream("[]", new DataOutputStream(output));
            }
        }
    }

    @Override
    public void setState(InputStream input) throws Exception {
        String estadoRecebido = (String) Util.objectFromStream(new DataInputStream(input));
        synchronized (clientes) {
            try (FileWriter file = new FileWriter(caminhoJson)) {
                file.write(estadoRecebido);
                file.flush();
            }
            System.out.println("[SERVIDOR] Estado atualizado a partir do cluster.");
        }
    }

    private void propagarAtualizacao(String operacao, String nome, String valor) {
        try {
            System.out.println("[SERVIDOR] Propagando atualização.");
            String mensagem = operacao + ":" + nome + ":" + valor;
            Message msg = new ObjectMessage(null, mensagem); // Envia para todos os nós do cluster
            channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean verificaRegistroRMI(String name) {
        try {
            Registry registry = LocateRegistry.getRegistry();
            return registry.lookup(name) != null;
        } catch (NotBoundException | RemoteException e) {
            return false;
        }
    }

    private void configurarRMI() throws RemoteException {
        if (!isCoordenador) {
            System.out.println("[SERVIDOR] Não sou o coordenador, então não configuro o RMI.");
            return;
        }

        try {
            Registry registry = LocateRegistry.getRegistry(1099);
            registry.list(); // Verifica se já existe um registro
            System.out.println("[SERVIDOR] Conectado ao registro RMI existente.");
        } catch (RemoteException e) {
            System.out.println("[SERVIDOR] Nenhum registro RMI encontrado. Criando um novo...");
            LocateRegistry.createRegistry(1099);
        }

        try {
            // Verifica se o objeto já foi exportado
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception e) {
            // Ignora a exceção se o objeto ainda não tiver sido exportado
        }

        BancoGatewayInterface stub = (BancoGatewayInterface) UnicastRemoteObject.exportObject(this, 0);
        LocateRegistry.getRegistry(1099).rebind("BancoCluster", stub);
        System.out.println("[SERVIDOR] Servidor registrado no RMI.");
    }

    @Override
    public void viewAccepted(View newView) {
        System.out.println("[SERVIDOR] Nova visão do cluster: " + newView);

        // Identifica o coordenador do cluster
        Address coordenadorAtual = newView.getMembers().getFirst();

        if (coordenadorAtual.equals(channel.getAddress())) {
            System.out.println("[SERVIDOR] Este servidor agora é o coordenador.");

            try {
                // Verifica se o RMI já está rodando antes de recriar
                Registry registry;
                try {
                    registry = LocateRegistry.getRegistry(1099);
                    registry.list(); // Verifica se o registro já está ativo
                    System.out.println("[SERVIDOR] Registro RMI já existente. Assumindo controle.");
                } catch (RemoteException e) {
                    System.out.println("[SERVIDOR] Nenhum registro RMI encontrado. Criando um novo...");
                    registry = LocateRegistry.createRegistry(1099);
                }

                // Verifica se o objeto RMI já foi registrado antes de exportá-lo novamente
                if (!verificaRegistroRMI("BancoCluster")) {
                    BancoGatewayInterface stub = (BancoGatewayInterface) UnicastRemoteObject.exportObject(this, 0);
                    registry.rebind("BancoCluster", stub);
                    System.out.println("[SERVIDOR] Registro RMI atualizado pelo novo coordenador.");
                } else {
                    System.out.println("[SERVIDOR] RMI já registrado. Nenhuma ação necessária.");
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            }

            this.isCoordenador = true;
        } else {
            System.out.println("[SERVIDOR] Coordenador atual: " + coordenadorAtual);
            this.isCoordenador = false;
        }
    }

    private void salvarEstado() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("estado_banco.dat"))) {
            oos.writeObject(contas);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void carregarEstado() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("estado_banco.dat"))) {
            contas = (HashMap<Integer, Conta>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[SERVIDOR] Nenhum estado anterior encontrado.");
        }
    }

    @Override
    public Object handle(Message msg) throws Exception {
        Object object = msg.getObject();

        return "Mensagem inválida.";
    }

    private void atualizarSaldoNoArquivo(Conta conta) {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            System.out.println("[SERVIDOR] Arquivo JSON vazio ou não encontrado.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteúdo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                // Localiza a conta e atualiza o saldo
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.getInt("id") == conta.getId()) {
                        cliente.put("saldo", conta.getSaldo());
                        break;
                    }
                }

                // Escreve o array atualizado de volta no arquivo
                try (FileWriter file = new FileWriter(arquivo)) {
                    file.write(clientesArray.toString(4));
                    file.flush();
                }

                System.out.println("[SERVIDOR] Saldo atualizado no arquivo JSON.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void salvarCadastroEmArquivo(String nome, String senha) {
        try {
            // Verifica se o nome ja existe no json
            if (clienteExistente(nome)) {
                System.out.println("[SERVIDOR] Cliente com o nome " + nome + " já cadastrado.");
                return; // Não permite cadastrar um novo cliente com o mesmo nome
            }

            // Caminho do arquivo JSON
            File arquivo = new File(caminhoJson);

            JSONArray clientesArray = new JSONArray();
            int maiorId = 0;

            if (arquivo.exists() && arquivo.length() > 0) { // Verifica se o arquivo existe e não esta vazio
                // Leitura do conteudo atual do arquivo
                try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                    StringBuilder sb = new StringBuilder();
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        sb.append(linha);
                    }

                    // Tenta converter o conteudo do arquivo em um array JSON
                    String content = sb.toString().trim();
                    if (content.startsWith("[") && content.endsWith("]")) {
                        // Se o conteudo for um array valido, converte
                        clientesArray = new JSONArray(content);
                        // Determina o maior id no JSON
                        for (int i = 0; i < clientesArray.length(); i++) {
                            JSONObject cliente = clientesArray.getJSONObject(i);
                            if (cliente.has("id")) {
                                maiorId = Math.max(maiorId, cliente.getInt("id"));
                            }
                        }
                    } else {
                        // Se não for um array valido, cria um novo array vazio
                        clientesArray = new JSONArray();
                    }
                }
            }

            // Incrementa o ID para o próximo cliente
            idConta = maiorId + 1;

            // Cria um objeto JSON com os dados do cliente
            JSONObject json = new JSONObject();
            json.put("id", idConta);
            json.put("nome", nome);
            json.put("senha", senha);
            json.put("saldo", 1000);

            // Adiciona o novo cliente ao array
            clientesArray.put(json);

            // Escreve o array JSON de volta no arquivo
            try (FileWriter file = new FileWriter(arquivo)) {
                file.write(clientesArray.toString(4)); // '4' adiciona identação para melhorar a leitura
                file.flush();
            }

            System.out.println("[SERVIDOR] Cadastro de cliente armazenado no arquivo.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Verifica se o cliente ja existe pelo nome
    private boolean clienteExistente(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Tenta converter o conteudo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equals(nome)) {
                            return true; // Cliente ja existe
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false; // Cliente não encontrado
    }

    private BigDecimal consultarSaldoClientePorId(int idConta) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getInt("id") == idConta) {
                            // Retorna o saldo como BigDecimal
                            return new BigDecimal(cliente.getDouble("saldo"));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Retorna BigDecimal.ZERO caso o cliente não seja encontrado
        return BigDecimal.ZERO;
    }

    private String consultarSaldoClientePorNome(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            // Retorna apenas o saldo do cliente
                            return String.valueOf(cliente.getDouble("saldo")); // Supondo que saldo é um campo numérico
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado.";
    }

    private String consultarIdClienteOrigem(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            // Se o cliente for encontrado, retorne o ID da conta no formato esperado
                            int idConta = cliente.getInt("id"); // Supondo que o ID está no campo "id"
                            return "[SERVIDOR] Conta encontrada: origem=" + idConta; // Retorna a mensagem com o ID da
                                                                                     // conta
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado."; // Caso o cliente não seja encontrado
    }

    private String consultarIdClienteDestino(String nome) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um array JSON
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                            // Se o cliente for encontrado, retorne o ID da conta no formato esperado
                            int idConta = cliente.getInt("id"); // Supondo que o ID está no campo "id"
                            return "[SERVIDOR] Conta encontrada: destino=" + idConta; // Retorna a mensagem com o ID da
                                                                                      // conta
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "[SERVIDOR] Cliente não encontrado."; // Caso o cliente não seja encontrado
    }

    private String somarSaldosClientes() {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            return "0";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteúdo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                double somaSaldos = 0;
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.has("saldo")) {
                        somaSaldos += cliente.getDouble("saldo");
                    }
                }

                return String.valueOf(somaSaldos); // Retorna apenas o número como string
            } else {
                return "0"; // Em caso de erro, retorna 0
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "0"; // Evita erro ao tentar converter
        }
    }

    private String removerClienteDoArquivo(String nome) {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            return "[SERVIDOR] Arquivo JSON vazio ou não encontrado.";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteudo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                // Percorre o array e remove o cliente com o nome correspondente
                boolean clienteRemovido = false;
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                        clientesArray.remove(i); // Remove o cliente
                        clienteRemovido = true;
                        break;
                    }
                }

                if (clienteRemovido) {
                    // Salva o array atualizado de volta no arquivo
                    try (FileWriter file = new FileWriter(arquivo)) {
                        file.write(clientesArray.toString(4)); // '4' para identação
                        file.flush();
                    }
                    return "[SERVIDOR] Cliente " + nome + " removido com sucesso.";
                } else {
                    return "[SERVIDOR] Cliente " + nome + " não encontrado.";
                }
            } else {
                return "[SERVIDOR] Formato de arquivo JSON inválido.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "[SERVIDOR] Erro ao processar a remoção do cliente.";
        }
    }

    private String alterarSenhaCliente(String nome, String novaSenha) {
        File arquivo = new File(caminhoJson);

        if (!arquivo.exists() || arquivo.length() == 0) {
            return "[SERVIDOR] Arquivo JSON vazio ou não encontrado.";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder sb = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha);
            }

            // Converte o conteudo do arquivo em um JSONArray
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                JSONArray clientesArray = new JSONArray(content);

                // Percorre o array para localizar o cliente e alterar a senha
                boolean clienteAlterado = false;
                for (int i = 0; i < clientesArray.length(); i++) {
                    JSONObject cliente = clientesArray.getJSONObject(i);
                    if (cliente.getString("nome").equalsIgnoreCase(nome)) {
                        cliente.put("senha", novaSenha); // Atualiza a senha
                        clienteAlterado = true;
                        break;
                    }
                }

                if (clienteAlterado) {
                    // Salva o array atualizado de volta no arquivo
                    try (FileWriter file = new FileWriter(arquivo)) {
                        file.write(clientesArray.toString(4));
                        file.flush();
                    }
                    return "[SERVIDOR] Senha do cliente " + nome + " alterada com sucesso.";
                } else {
                    return "[SERVIDOR] Cliente " + nome + " não encontrado.";
                }
            } else {
                return "[SERVIDOR] Formato de arquivo JSON inválido.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "[SERVIDOR] Erro ao processar a alteração da senha.";
        }
    }

    private boolean verificarCredenciais(String nome, String senha) {
        File arquivo = new File(caminhoJson);

        if (arquivo.exists() && arquivo.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    sb.append(linha);
                }

                // Converte o conteúdo do arquivo em um JSONArray
                String content = sb.toString().trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    JSONArray clientesArray = new JSONArray(content);

                    // Verifica o nome e a senha
                    for (int i = 0; i < clientesArray.length(); i++) {
                        JSONObject cliente = clientesArray.getJSONObject(i);
                        if (cliente.getString("nome").equals(nome) && cliente.getString("senha").equals(senha)) {
                            return true; // Credenciais válidas
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false; // Credenciais inválidas
    }
}