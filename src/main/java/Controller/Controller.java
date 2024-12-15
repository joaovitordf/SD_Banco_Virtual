package Controller;


import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.util.*;

import java.util.*;


public class Controller {
    private JChannel channel;
    MessageDispatcher  despachante;

    private void start() throws Exception {

        //Cria o canal de comunicação com uma configuração XML do JGroups
    	// canal = new JChannel();
        // canal = new JChannel("sequencer.xml");
        // canal = new JChannel("cast.xml");        
        canal.setReceiver(this);   //quem irá lidar com as mensagens recebidas
        
                     // MessageDispatcher(Channel channel, MessageListener l1, MembershipListener l2, RequestHandler req_handler) 
        despachante=new MessageDispatcher(canal, this, this, this); // canal, quem tem receive(), quem tem viewAccecpted(), quem tem handle()
            
        canal.connect("TiposDeCast");
           eventLoop();
        canal.close();

    }

    public void receive(Message msg) { //exibe mensagens recebidas
        System.out.println("" + msg.getSrc() + ": " + msg.getObject()); // DEBUG
    }

    public void viewAccepted(View new_view) { //exibe alterações na composição do cluster
        System.err.println("\t\t\t\t\t[DEBUG] ** view: " + new_view);
    }

    class Pedido{
        final static int TIPO_SALDO = 0;
        final static int TIPO_TRANSFERENCIA = 1;

        int tipoPedido; 
        int numConta; 

        float valor; 
        int contaDestino; 

    }


    class Conta{
        int numConta; 
        float saldo; 
    }

    HashMap<Integer,Conta> contas = new HashMap<Integer,Conta>(); 

    public Object handle(Message msg) throws Exception{ // responde requisições recebidas
        // aqui você implementará suas regras de negócio conforme as solicitações recebidas via handle()

        Pedido pedido = (Pedido) msg.getObject(); 

        switch( pedido.tipoPedido ){

            case: Pedido.TIPO_SALDO
                Conta c = contas.get( pedido.numConta ); 
                return c.saldo; 
                //break;

            case: Pedido.TIPO_TRANSFERENCIA
                //...
                break;

            default: 
                // pedido inválido
        }

    }

}
