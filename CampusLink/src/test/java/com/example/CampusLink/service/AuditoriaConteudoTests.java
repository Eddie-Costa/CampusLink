// verificacao dos registros de materiais exercita denuncias e revisoes com consultas simuladas
package com.example.CampusLink.service;

import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DenunciaRepository;
import com.example.CampusLink.repository.RevisaoConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// testes da auditoria de conteudos esta classe confere mensagens de sucesso recusa e erro
class AuditoriaConteudoTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ConteudoRepository conteudos = mock(ConteudoRepository.class);
    private final DenunciaRepository denuncias = mock(DenunciaRepository.class);

    // testar denuncia repetida no historico este teste confere o registro da recusa sem criar outro relato
    @Test
    void denunciaDuplicadaRegistraRecusaSemInserirNovaDenuncia() throws Exception {
        DenunciaService service = denuncias();
        when(conteudos.buscarStatus(10L)).thenReturn("ativo");
        when(denuncias.alunoJaDenunciou(10L, 20L)).thenReturn(true);
        assertEquals("duplicada", service.registrarDenuncia(10L, 20L, 30L, "motivo"));
        verify(denuncias, never()).inserir(any());
        verificarLog("WARN", "registrarDenuncia", "Denúncia duplicada.");
    }

    // testar suspensao por denuncias este teste confere a mudanca do material e os registros ao atingir o limite
    @Test
    void denunciaQueAtingeLimiteRegistraCadastroESuspensao() throws Exception {
        DenunciaService service = denuncias();
        when(conteudos.buscarStatus(10L)).thenReturn("ativo");
        when(conteudos.buscarIdProfessor(10L)).thenReturn(40L);
        when(denuncias.contarAlunosDaTurma(30L)).thenReturn(10);
        when(denuncias.contarDenunciasPendentes(10L)).thenReturn(2);
        assertEquals("suspenso", service.registrarDenuncia(10L, 20L, 30L, "motivo"));
        verify(conteudos).atualizarStatus(10L, "suspenso_denuncia");
        verificarLog("INFO", "registrarDenuncia", "Denúncia registrada.");
        verificarLog("INFO", "registrarDenuncia", "Conteúdo suspenso automaticamente");
    }

    // testar registro da revisao este teste confere que liberacao e reprovacao registram o resultado real
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void revisaoRegistraResultadoRealDaLiberacaoEReprovacao(boolean sucesso) throws Exception {
        RevisaoConteudoRepository repository = mock(RevisaoConteudoRepository.class);
        RevisaoConteudoService service = new RevisaoConteudoService(repository);
        ReflectionTestUtils.setField(service, "UsuarioRepository", usuarios);
        when(repository.liberarConteudo(10L)).thenReturn(sucesso ? 1 : 0);
        when(repository.reprovarConteudo(10L, "motivo")).thenReturn(sucesso ? 1 : 0);
        assertEquals(sucesso, service.liberarConteudo(10L));
        assertEquals(sucesso, service.reprovarConteudo(10L, "motivo"));
        verificarLog(sucesso ? "INFO" : "WARN", "liberarConteudo", sucesso ? "Conteúdo liberado" : "Liberação recusada");
        verificarLog(sucesso ? "INFO" : "WARN", "reprovarConteudo", sucesso ? "Conteúdo reprovado" : "Reprovação recusada");
    }

    // testar registro das acoes do professor este teste confere sucesso ou recusa na remocao e no pedido de revisao
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acoesDoProfessorRegistramSucessoOuRecusa(boolean sucesso) throws Exception {
        ConteudoService service = new ConteudoService(conteudos, mock(ArquivoService.class));
        ReflectionTestUtils.setField(service, "UsuarioRepository", usuarios);
        when(conteudos.removerConteudoProfessor(10L, 40L)).thenReturn(sucesso);
        when(conteudos.solicitarRevisao(10L, 40L)).thenReturn(sucesso);
        assertEquals(sucesso, service.removerConteudoProfessor(10L, 40L));
        assertEquals(sucesso, service.solicitarRevisao(10L, 40L));
        verificarLog(sucesso ? "INFO" : "WARN", "removerConteudoProfessor", sucesso ? "Conteúdo removido" : "Remoção de conteúdo recusada");
        verificarLog(sucesso ? "INFO" : "WARN", "solicitarRevisao", sucesso ? "Revisão de conteúdo solicitada" : "Solicitação de revisão recusada");
    }

    // testar falha na denuncia este teste confere a preservacao da causa e o registro do erro
    @Test
    void erroDeDenunciaMantemCausaERegistraExcecao() throws Exception {
        DenunciaService service = denuncias();
        SQLException erro = new SQLException("falha simulada");
        when(conteudos.buscarStatus(10L)).thenThrow(erro);
        assertSame(erro, assertThrows(RuntimeException.class,
                () -> service.registrarDenuncia(10L, 20L, 30L, "motivo")).getCause());
        verify(usuarios).InserirLogsNoBD(isNull(), eq("ERROR"), anyString(), eq("registrarDenuncia"),
                isNull(), contains("conteudoId=10"), isNull(), contains("SQLException: falha simulada"));
    }

    // preparar servico de denuncias do teste este metodo monta o servico com banco e historico simulados
    private DenunciaService denuncias() {
        DenunciaService service = new DenunciaService(denuncias, conteudos);
        ReflectionTestUtils.setField(service, "UsuarioRepository", usuarios);
        return service;
    }

    // conferir registro de conteudo este metodo verifica nivel operacao e trecho da mensagem esperada
    private void verificarLog(String nivel, String operacao, String mensagem) throws Exception {
        verify(usuarios).InserirLogsNoBD(isNull(), eq(nivel), anyString(), eq(operacao),
                isNull(), contains(mensagem), isNull(), isNull());
    }
}
