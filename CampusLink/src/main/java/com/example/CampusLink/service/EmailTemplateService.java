// modelos de email organizam o conteudo das mensagens enviadas
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// montagem dos emails esta classe preenche os modelos e aplica os estilos para os leitores de email
@Service
public class EmailTemplateService {
    private final SpringTemplateEngine templateEngine;

    @Value("${campuslink.url-base:}")
    private String urlBase;

    // preparar modelos de email este construtor recebe o recurso usado para preencher as mensagens
    public EmailTemplateService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    // montar email com codigo este metodo preenche o modelo de verificacao com a finalidade escolhida
    public String codigo(String codigo, FinalidadeCodigoEmail finalidade) {
        Context contexto = contexto(finalidade.getTitulo(), finalidade.getDescricao());
        contexto.setVariable("codigo", codigo);
        return renderizar("codigo", contexto);
    }

    // montar email com dados este metodo preenche o modelo de exportacao do cadastro
    public String dados(DadosUsuarioDTO usuario) {
        Context contexto = contexto("Seus dados pessoais", "Aqui está a cópia dos dados cadastrais que você solicitou.");
        contexto.setVariable("campos", camposDados(usuario));
        return renderizar("dados", contexto);
    }

    // listar campos exportados este metodo organiza os dados pessoais e seus rotulos na ordem do email
    public Map<String, String> camposDados(DadosUsuarioDTO usuario) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Nome", valor(usuario.getNome()));
        campos.put("E-mail", valor(usuario.getEmail()));
        campos.put("Telefone", valor(usuario.getTelefone()));
        campos.put("Nascimento", valor(usuario.getDataNascimento()));
        campos.put("Tipo de conta", usuario.getTipoConta());
        campos.put(usuario.getRotuloIdentificador(), valor(usuario.getIdentificador()));
        campos.put("Cadastro", valor(usuario.getDataCadastro()));
        return campos;
    }

    // montar email de atendimento este metodo preenche protocolo situacao data e link do chamado
    public String chamado(ChamadoDTO chamado) {
        boolean concluido = "concluido".equals(chamado.getStatus());
        Context contexto = contexto(concluido ? "Chamado concluído" : "Chamado registrado",
                concluido ? "Seu atendimento foi concluído. O histórico completo está no PDF anexado."
                        : "Recebemos sua solicitação. Nossa equipe responderá pelo CampusLink.");
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
                .withZone(ZoneId.of("America/Sao_Paulo"));
        contexto.setVariable("chamado", chamado);
        contexto.setVariable("concluido", concluido);
        contexto.setVariable("data", formato.format(concluido ? chamado.getConcluidoEm() : chamado.getCreatedAt()));
        contexto.setVariable("linkChamado", linkChamado(chamado.getId()));
        return renderizar("chamado", contexto);
    }

    // preparar dados basicos do email este metodo organiza titulo e descricao para preencher o modelo
    private Context contexto(String titulo, String descricao) {
        Context contexto = new Context();
        contexto.setVariable("titulo", titulo);
        contexto.setVariable("descricao", descricao);
        return contexto;
    }

    // formatar valor do cadastro este metodo formata datas e indica campos sem informacao
    private String valor(Object dado) {
        // mostrar dado ausente usa uma mensagem quando o valor nao foi informado
        if (dado == null || dado.toString().isBlank()) return "Não informado";
        // formatar data no email apresenta dia mes e ano para valores de data
        if (dado instanceof LocalDate data) return data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return dado.toString();
    }

    // preencher modelo de email este metodo gera o conteudo e aplica os estilos aos elementos da mensagem
    private String renderizar(String modelo, Context contexto) {
        // montar modelo de email tenta preencher o modelo e aplicar os estilos na mensagem
        try {
            String html = templateEngine.process("Emails/" + modelo, contexto);
            String css = new ClassPathResource("static/css/Emails/email.css").getContentAsString(StandardCharsets.UTF_8);

            Matcher regras = Pattern.compile("\\.([a-zA-Z][a-zA-Z0-9-]*)\\s*\\{([^{}]+)}").matcher(css);
            while (regras.find()) {
                String classe = regras.group(1);
                String estilo = regras.group(2).replaceAll("\\s+", " ").trim();
                html = html.replace("class=\"" + classe + "\"", "class=\"" + classe + "\" style=\"" + estilo + "\"");
            }
            return html;
        } catch (IOException | TemplateEngineException e) {
            throw new MailPreparationException("Não foi possível montar o e-mail.", e);
        }
    }

    // montar link do atendimento este metodo usa o endereco configurado quando ele permite acesso pela web
    private String linkChamado(Long id) {
        // montar link do chamado ignora uma base de endereco vazia
        if (urlBase == null || urlBase.isBlank()) return null;
        String base = urlBase.strip().replaceAll("/+$", "");
        // validar link do chamado aceita apenas enderecos com http ou https
        if (!base.startsWith("https://") && !base.startsWith("http://")) return null;
        return base + "/meus-chamados/" + id;
    }
}
