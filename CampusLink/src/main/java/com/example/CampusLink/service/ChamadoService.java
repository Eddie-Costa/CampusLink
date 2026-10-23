package com.example.CampusLink.service;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.repository.ChamadoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ChamadoService {

    private final ChamadoRepository chamadoRepository;

    public ChamadoService(ChamadoRepository chamadoRepository) {
        this.chamadoRepository = chamadoRepository;
    }

    @Transactional
    public ChamadoDTO abrir(DadosUsuarioDTO usuario, ChamadoDTO formulario) {
        validarUsuario(usuario);
        String assunto = validarTexto(formulario.getAssunto(), "O assunto", 150);
        String descricao = validarTexto(formulario.getDescricao(), "A descrição", 5000);
        Long id = chamadoRepository.inserir(usuario.getId(), assunto, descricao);
        return chamadoRepository.buscarDoUsuario(id, usuario.getId());
    }

    public List<ChamadoDTO> listarDoUsuario(DadosUsuarioDTO usuario) {
        validarUsuario(usuario);
        return chamadoRepository.listarDoUsuario(usuario.getId());
    }

    public List<ChamadoDTO> listarParaAdmin(DadosUsuarioDTO admin, String status) {
        validarAdmin(admin);
        if (!List.of("", "aberto", "respondido", "concluido").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação inválida");
        }
        return chamadoRepository.listarParaAdmin(status);
    }

    public ChamadoDTO buscarDoUsuario(Long id, DadosUsuarioDTO usuario) {
        validarUsuario(usuario);
        ChamadoDTO chamado = chamadoRepository.buscarDoUsuario(id, usuario.getId());
        return carregarRespostas(chamado);
    }

    public ChamadoDTO buscarParaAdmin(Long id, DadosUsuarioDTO admin) {
        validarAdmin(admin);
        return carregarRespostas(chamadoRepository.buscarPorId(id, false));
    }

    @Transactional
    public void responder(Long id, DadosUsuarioDTO admin, String texto) {
        validarAdmin(admin);
        String resposta = validarTexto(texto, "A resposta", 5000);
        // bloqueia o chamado ate terminar, evitando responder enquanto outro admin conclui
        ChamadoDTO chamado = chamadoRepository.buscarPorId(id, true);
        verificarExistencia(chamado);
        if ("concluido".equals(chamado.getStatus())) {
            throw new IllegalStateException("Este chamado já foi concluído e não pode receber respostas.");
        }
        chamadoRepository.inserirResposta(id, admin.getId(), resposta);
    }

    @Transactional
    public ChamadoDTO concluir(Long id, DadosUsuarioDTO admin) {
        validarAdmin(admin);
        ChamadoDTO chamado = chamadoRepository.buscarPorId(id, true);
        verificarExistencia(chamado);
        if ("concluido".equals(chamado.getStatus())) return null;

        chamado.setRespostas(chamadoRepository.listarRespostas(id));
        if (!chamado.isPossuiRespostaAdmin()) {
            throw new IllegalStateException("Responda ao usuário antes de concluir o chamado.");
        }
        chamadoRepository.concluir(id, admin.getId());
        return carregarRespostas(chamadoRepository.buscarPorId(id, false));
    }

    @Transactional
    public void enviarMensagemUsuario(Long id, DadosUsuarioDTO usuario, String texto) {
        validarUsuario(usuario);
        String mensagem = validarTexto(texto, "Mensagem", 5000);
        ChamadoDTO chamado = chamadoRepository.buscarDoUsuario(id, usuario.getId(), true);
        verificarExistencia(chamado);
        if ("concluido".equals(chamado.getStatus())) {
            throw new IllegalStateException("Este chamado já foi concluído. Abra outro chamado se precisar de ajuda.");
        }
        chamadoRepository.inserirMensagemUsuario(id, usuario.getId(), mensagem);
    }

    private ChamadoDTO carregarRespostas(ChamadoDTO chamado) {
        verificarExistencia(chamado);
        chamado.setRespostas(chamadoRepository.listarRespostas(chamado.getId()));
        return chamado;
    }

    private void verificarExistencia(ChamadoDTO chamado) {
        if (chamado == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chamado não encontrado");
    }

    private void validarUsuario(DadosUsuarioDTO usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private void validarAdmin(DadosUsuarioDTO usuario) {
        validarUsuario(usuario);
        if (!"ADMINISTRADOR".equals(usuario.getPerfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String validarTexto(String texto, String campo, int limite) {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Preencha o campo: " + campo + ".");
        if (texto.length() > limite) throw new IllegalArgumentException(campo + " deve ter no máximo " + limite + " caracteres.");
        return texto.strip();
    }
}
