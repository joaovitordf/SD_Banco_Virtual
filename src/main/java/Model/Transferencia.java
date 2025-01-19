package Model;


import java.math.BigDecimal;

public class Transferencia implements java.io.Serializable {

    private int idOrigem;
    private int idDestino;
    private BigDecimal valor;
    private String erro;

    public Transferencia() {

    }

    public Transferencia(int idOrigem, int idDestino, BigDecimal valor) {
        this.idOrigem = idOrigem;
        this.idDestino = idDestino;
        this.valor = valor;
    }

    public int getIdOrigem() {
        return idOrigem;
    }

    public void setIdOrigem(int idOrigem) {
        this.idOrigem = idOrigem;
    }

    public int getIdDestino() {
        return idDestino;
    }

    public void setIdDestino(int idDestino) {
        this.idDestino = idDestino;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }
}
