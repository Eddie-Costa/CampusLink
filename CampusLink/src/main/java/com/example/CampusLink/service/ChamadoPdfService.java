package com.example.CampusLink.service;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HexFormat;

@Service
public class ChamadoPdfService {

    private static final Color AZUL = new Color(17, 24, 63);
    private static final Color DESTAQUE = new Color(45, 156, 229);
    private static final Color TEXTO = new Color(52, 64, 84);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public byte[] gerar(ChamadoDTO chamado) {
        if (!"concluido".equals(chamado.getStatus()) || chamado.getConcluidoEm() == null) {
            throw new MailPreparationException("O PDF exige um chamado concluído.");
        }
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream arquivo = new ByteArrayOutputStream()) {
            documento.getDocumentInformation().setTitle("CampusLink - Histórico do chamado #" + chamado.getId());
            documento.getDocumentInformation().setAuthor("CampusLink");
            documento.getDocumentInformation().setCreator("CampusLink - Canal de ajuda");
            documento.getDocumentInformation().setCreationDate(Calendar.getInstance());
            Pagina pagina = new Pagina(documento, chamado.getId());
            try (pagina) {
                pagina.texto("Registro de atendimento", 22, true, AZUL);
                pagina.texto("Chamado #" + chamado.getId() + " | Concluído", 12, true, DESTAQUE);
                pagina.espaco(12);
                pagina.texto(chamado.getAssunto(), 15, true, AZUL);
                pagina.espaco(8);
                pagina.texto("Solicitante: " + chamado.getNomeUsuario() + " (ID " + id(chamado.getIdUsuario()) + ")", 10, false, TEXTO);
                pagina.texto("E-mail: " + chamado.getEmailUsuario(), 10, false, TEXTO);
                pagina.texto("Abertura: " + DATA.format(chamado.getCreatedAt()), 10, false, TEXTO);
                pagina.texto("Conclusão: " + DATA.format(chamado.getConcluidoEm()), 10, false, TEXTO);
                pagina.texto("Administrador responsável pela conclusão: ID " + id(chamado.getIdAdminConclusao()), 10, false, TEXTO);
                pagina.texto("Emitido em: " + DATA.format(OffsetDateTime.now()), 10, false, TEXTO);
                pagina.texto("Fuso dos horários: America/Sao_Paulo (Brasília).", 9, false, TEXTO);
                pagina.espaco(8);
                pagina.texto("Histórico completo", 15, true, AZUL);
                pagina.texto("Solicitação inicial e " + chamado.getRespostas().size() + " mensagem(ns), na ordem registrada.", 9, false, TEXTO);
                pagina.mensagem("Solicitação inicial", chamado.getNomeUsuario(), "Solicitante", chamado.getIdUsuario(), chamado.getCreatedAt(), chamado.getDescricao());
                for (ChamadoRespostaDTO resposta : chamado.getRespostas()) {
                    boolean admin = "admin".equals(resposta.getTipoAutor());
                    pagina.mensagem("Mensagem #" + resposta.getId(), resposta.getNomeAutor(),
                            admin ? "Equipe CampusLink" : "Solicitante",
                            admin ? resposta.getIdAdminUsuario() : resposta.getIdUsuario(),
                            resposta.getCreatedAt(), resposta.getResposta());
                }
                pagina.reservar(pagina.caracteresRepresentados ? 90 : 60);
                pagina.espaco(12);
                pagina.texto("Sobre este registro", 11, true, AZUL);
                pagina.texto("Documento gerado a partir dos dados registrados no CampusLink ao encerrar o atendimento. "
                        + "Os nomes correspondem ao cadastro consultado na emissão. Este arquivo não contém assinatura digital.", 9, false, TEXTO);
                if (pagina.caracteresRepresentados) {
                    pagina.texto("Caracteres sem representação na fonte e controles são identificados por seu código Unicode, no formato [U+XXXX].", 9, false, TEXTO);
                }
            }
            adicionarRodapes(documento, chamado.getId(), pagina.normal);
            documento.save(arquivo);
            return arquivo.toByteArray();
        } catch (IOException | IllegalArgumentException e) {
            throw new MailPreparationException("Não foi possível gerar o PDF do chamado.", e);
        }
    }

    public String sha256(byte[] arquivo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(arquivo));
        } catch (NoSuchAlgorithmException e) {
            throw new MailPreparationException("Não foi possível calcular a integridade do PDF.", e);
        }
    }

    private static String id(Long valor) {
        return valor == null ? "indisponível" : valor.toString();
    }

    private void adicionarRodapes(PDDocument documento, Long idChamado, PDFont fonte) throws IOException {
        for (int i = 0; i < documento.getNumberOfPages(); i++) {
            try (PDPageContentStream rodape = new PDPageContentStream(documento, documento.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                rodape.setStrokingColor(new Color(220, 226, 236));
                rodape.moveTo(48, 47);
                rodape.lineTo(547, 47);
                rodape.stroke();
                escrever(rodape, fonte, "CampusLink | Chamado #" + idChamado + " | Página " + (i + 1) + " de " + documento.getNumberOfPages(), 8, TEXTO, 48, 30);
            }
        }
    }

    private static void escrever(PDPageContentStream fluxo, PDFont fonte, String texto, float tamanho, Color cor, float x, float y) throws IOException {
        fluxo.beginText();
        fluxo.setFont(fonte, tamanho);
        fluxo.setNonStrokingColor(cor);
        fluxo.newLineAtOffset(x, y);
        fluxo.showText(texto);
        fluxo.endText();
    }

    // cada geração tem suas próprias fontes e páginas, sem compartilhar estado entre envios
    private static class Pagina implements AutoCloseable {
        private final PDDocument documento;
        private final PDFont normal;
        private final PDFont negrito;
        private final PDImageXObject logo;
        private final Long idChamado;
        private PDPageContentStream fluxo;
        private float y;
        private String continuacao;
        private boolean caracteresRepresentados;

        Pagina(PDDocument documento, Long idChamado) throws IOException {
            this.documento = documento;
            this.idChamado = idChamado;
            normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            logo = PDImageXObject.createFromByteArray(documento, new ClassPathResource("static/images/LogoCampusLink.png").getContentAsByteArray(), "CampusLink");
            novaPagina();
        }

        private void novaPagina() throws IOException {
            if (fluxo != null) fluxo.close();
            PDPage folha = new PDPage(PDRectangle.A4);
            documento.addPage(folha);
            fluxo = new PDPageContentStream(documento, folha);
            fluxo.setNonStrokingColor(AZUL);
            fluxo.addRect(0, 744, folha.getMediaBox().getWidth(), 98);
            fluxo.fill();
            escrever(fluxo, negrito, "CampusLink", 21, Color.WHITE, 48, 797);
            escrever(fluxo, normal, "CANAL DE AJUDA | Protocolo #" + idChamado, 9, new Color(190, 222, 248), 48, 774);
            fluxo.drawImage(logo, 467, 762, 80, 80f * logo.getHeight() / logo.getWidth());
            fluxo.setNonStrokingColor(DESTAQUE);
            fluxo.addRect(0, 740, folha.getMediaBox().getWidth(), 4);
            fluxo.fill();
            y = 711;
            if (continuacao != null) {
                escrever(fluxo, negrito, continuacao + " (continuação)", 9, DESTAQUE, 48, y);
                y -= 24;
            }
        }

        void espaco(float altura) { y -= altura; }

        void reservar(float altura) throws IOException {
            if (y - altura < 70) novaPagina();
        }

        void mensagem(String titulo, String autor, String papel, Long idAutor, OffsetDateTime data, String conteudo) throws IOException {
            if (y < 150) novaPagina();
            espaco(10);
            texto(titulo + " | " + papel, 11, true, DESTAQUE);
            continuacao = titulo;
            texto(autor + " (ID " + id(idAutor) + ")", 10, true, AZUL);
            texto(DATA.format(data), 9, false, TEXTO);
            espaco(6);
            texto(conteudo, 10, false, TEXTO);
            continuacao = null;
            espaco(5);
        }

        void texto(String valor, float tamanho, boolean forte, Color cor) throws IOException {
            PDFont fonte = forte ? negrito : normal;
            String seguro = representavel(valor == null ? "" : valor, fonte);
            for (String paragrafo : seguro.split("\n", -1)) {
                if (paragrafo.isEmpty()) { espaco(tamanho * 1.5f); continue; }
                int inicio = 0;
                while (inicio < paragrafo.length()) {
                    float largura = 0;
                    int fim = inicio;
                    int ultimoEspaco = -1;
                    while (fim < paragrafo.length()) {
                        int proximo = fim + Character.charCount(paragrafo.codePointAt(fim));
                        float avancar = fonte.getStringWidth(paragrafo.substring(fim, proximo)) / 1000 * tamanho;
                        if (largura + avancar > 499) break;
                        largura += avancar;
                        if (paragrafo.charAt(fim) == ' ') ultimoEspaco = fim;
                        fim = proximo;
                    }
                    if (fim < paragrafo.length() && ultimoEspaco > inicio) fim = ultimoEspaco + 1;
                    if (y < 70) novaPagina();
                    escrever(fluxo, fonte, paragrafo.substring(inicio, fim), tamanho, cor, 48, y);
                    y -= tamanho * 1.5f;
                    inicio = fim;
                }
            }
        }

        private String representavel(String texto, PDFont fonte) throws IOException {
            StringBuilder resultado = new StringBuilder();
            String normalizado = texto.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
            for (int codigo : normalizado.codePoints().toArray()) {
                String caractere = new String(Character.toChars(codigo));
                if (codigo == '\n') { resultado.append('\n'); continue; }
                try {
                    if (Character.isISOControl(codigo) || Character.getType(codigo) == Character.FORMAT) throw new IllegalArgumentException();
                    fonte.encode(caractere);
                    resultado.append(caractere);
                } catch (IllegalArgumentException e) {
                    resultado.append("[U+").append(String.format("%04X", codigo)).append(']');
                    caracteresRepresentados = true;
                }
            }
            return resultado.toString();
        }

        @Override
        public void close() throws IOException { if (fluxo != null) fluxo.close(); }
    }
}
