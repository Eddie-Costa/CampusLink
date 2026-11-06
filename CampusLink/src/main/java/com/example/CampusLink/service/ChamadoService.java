// regras de atendimento organizam abertura conversa e conclusao de chamados
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.repository.ChamadoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// servico de suporte esta classe confere autoria perfil textos e impedimentos de chamados concluidos
@Service
public class ChamadoService {

    private final ChamadoRepository chamadoRepository;

    // preparar regras dos chamados este construtor recebe as consultas e gravacoes dos atendimentos
    public ChamadoService(ChamadoRepository chamadoRepository) {
        this.chamadoRepository = chamadoRepository;
    }

    // abrir chamado validado este metodo confere o usuario e os textos antes de salvar o pedido
    @Transactional
    public ChamadoDTO abrir(DadosUsuarioDTO usuario, ChamadoDTO formulario) {
        validarUsuario(usuario);
        String assunto = validarTexto(formulario.getAssunto(), "O assunto", 150);
        String descricao = validarTexto(formulario.getDescricao(), "A descrição", 5000);
        Long id = chamadoRepository.inserir(usuario.getId(), assunto, descricao);
        return chamadoRepository.buscarDoUsuario(id, usuario.getId());
    }

    // listar pedidos do usuario este metodo valida a conta antes de consultar seus chamados
    public List<ChamadoDTO> listarDoUsuario(DadosUsuarioDTO usuario) {
        validarUsuario(usuario);
        return chamadoRepository.listarDoUsuario(usuario.getId());
    }

    // listar atendimentos para administrador este metodo confere o perfil e valida o filtro de situacao
    public List<ChamadoDTO> listarParaAdmin(DadosUsuarioDTO admin, String status) {
        validarAdmin(admin);
        // validar filtro de chamados rejeita situacoes que nao fazem parte dos filtros aceitos
        if (!List.of("", "aberto", "respondido", "concluido").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação inválida");
        }
        return chamadoRepository.listarParaAdmin(status);
    }

    // buscar atendimento do titular este metodo confere o usuario e carrega as respostas do seu pedido
    public ChamadoDTO buscarDoUsuario(Long id, DadosUsuarioDTO usuario) {
        validarUsuario(usuario);
        ChamadoDTO chamado = chamadoRepository.buscarDoUsuario(id, usuario.getId());
        return carregarRespostas(chamado);
    }

    // buscar atendimento para administrador este metodo confere o perfil e carrega a conversa completa
    public ChamadoDTO buscarParaAdmin(Long id, DadosUsuarioDTO admin) {
        validarAdmin(admin);
        return carregarRespostas(chamadoRepository.buscarPorId(id, false));
    }

    // responder atendimento validado este metodo confere o administrador e impede respostas em chamados concluidos
    @Transactional
    public void responder(Long id, DadosUsuarioDTO admin, String texto) {
        validarAdmin(admin);
        String resposta = validarTexto(texto, "A resposta", 5000);

        ChamadoDTO chamado = chamadoRepository.buscarPorId(id, true);
        verificarExistencia(chamado);
        // responder chamado impede respostas quando o atendimento ja foi concluido
        if ("concluido".equals(chamado.getStatus())) {
            throw new IllegalStateException("Este chamado já foi concluído e não pode receber respostas.");
        }
        chamadoRepository.inserirResposta(id, admin.getId(), resposta);
    }

    // concluir atendimento validado este metodo exige resposta administrativa antes de encerrar o chamado
    @Transactional
    public ChamadoDTO concluir(Long id, DadosUsuarioDTO admin) {
        validarAdmin(admin);
        ChamadoDTO chamado = chamadoRepository.buscarPorId(id, true);
        verificarExistencia(chamado);
        // concluir chamado ignora uma nova conclusao de um atendimento ja encerrado
        if ("concluido".equals(chamado.getStatus())) return null;

        chamado.setRespostas(chamadoRepository.listarRespostas(id));
        // exigir resposta administrativa impede a conclusao antes de responder ao usuario
        if (!chamado.isPossuiRespostaAdmin()) {
            throw new IllegalStateException("Responda ao usuário antes de concluir o chamado.");
        }
        chamadoRepository.concluir(id, admin.getId());
        return carregarRespostas(chamadoRepository.buscarPorId(id, false));
    }

    // enviar mensagem validada do titular este metodo confere autoria e impede mensagens apos a conclusao
    @Transactional
    public void enviarMensagemUsuario(Long id, DadosUsuarioDTO usuario, String texto) {
        validarUsuario(usuario);
        String mensagem = validarTexto(texto, "Mensagem", 5000);
        ChamadoDTO chamado = chamadoRepository.buscarDoUsuario(id, usuario.getId(), true);
        verificarExistencia(chamado);
        // enviar mensagem no chamado bloqueia mensagens em atendimentos concluidos
        if ("concluido".equals(chamado.getStatus())) {
            throw new IllegalStateException("Este chamado já foi concluído. Abra outro chamado se precisar de ajuda.");
        }
        chamadoRepository.inserirMensagemUsuario(id, usuario.getId(), mensagem);
    }

    // carregar respostas do atendimento este metodo exige um chamado existente e adiciona suas mensagens
    private ChamadoDTO carregarRespostas(ChamadoDTO chamado) {
        verificarExistencia(chamado);
        chamado.setRespostas(chamadoRepository.listarRespostas(chamado.getId()));
        return chamado;
    }

    // verificar chamado existente este metodo informa quando o pedido nao foi encontrado
    private void verificarExistencia(ChamadoDTO chamado) {
        // buscar chamado informa que o chamado nao foi encontrado
        if (chamado == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chamado não encontrado");
    }

    // validar usuario do atendimento este metodo exige uma conta identificada
    private void validarUsuario(DadosUsuarioDTO usuario) {
        // validar usuario do chamado exige um usuario identificado
        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    // validar perfil administrativo do atendimento este metodo exige uma conta com perfil de administrador
    private void validarAdmin(DadosUsuarioDTO usuario) {
        validarUsuario(usuario);
        // validar administrador do chamado exige perfil administrativo
        if (!"ADMINISTRADOR".equals(usuario.getPerfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    // validar texto do atendimento este metodo exige preenchimento e respeita o limite de caracteres
    private String validarTexto(String texto, String campo, int limite) {
        // validar texto obrigatorio rejeita campos vazios
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Preencha o campo: " + campo + ".");
        // validar tamanho do texto rejeita campos acima do limite de caracteres
        if (texto.length() > limite) throw new IllegalArgumentException(campo + " deve ter no máximo " + limite + " caracteres.");
        return texto.strip();
    }
}
