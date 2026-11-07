// banco de chamados centraliza pedidos e mensagens de atendimento
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

// persistencia de atendimento esta classe consulta titulares ativos e grava conversas e conclusoes
@Repository
public class ChamadoRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SQL_CHAMADOS = """
            SELECT c.*, u.nome AS nome_usuario, u.email AS email_usuario
            FROM public."CHAMADOS" c
            JOIN public."USUARIOS" u ON u.id = c.id_usuario
            """;

    // preparar consulta de chamados este construtor recebe o acesso ao banco
    public ChamadoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // buscar titular ativo dos chamados este metodo confere email situacao e vinculo com o perfil
    public DadosUsuarioDTO buscarUsuarioAtivo(String email, String tipo) {
        String tabela;
        // consultar aluno do chamado escolhe os cadastros de alunos para esse perfil
        if ("aluno".equals(tipo)) tabela = "ALUNOS";
        // consultar professor do chamado escolhe os cadastros de professores para esse perfil
        else if ("professor".equals(tipo)) tabela = "PROFESSORES";
        // consultar administrador do chamado escolhe a tabela administrativa ou recusa o perfil desconhecido
        else if ("admin".equals(tipo)) tabela = "ADMINISTRADOR";
        else return null;

        String sql = "SELECT u.id, u.nome, u.email FROM public.\"USUARIOS\" u "
                + "WHERE LOWER(u.email) = ? AND u.status = true "
                + "AND EXISTS (SELECT 1 FROM public.\"" + tabela + "\" p WHERE p.id_usuario = u.id)";
        List<DadosUsuarioDTO> usuarios = jdbcTemplate.query(sql, (rs, linha) -> {
            DadosUsuarioDTO usuario = new DadosUsuarioDTO();
            usuario.setId(rs.getLong("id"));
            usuario.setNome(rs.getString("nome"));
            usuario.setEmail(rs.getString("email"));
            usuario.setPerfil(tabela);
            return usuario;
        }, email.trim().toLowerCase(Locale.ROOT));
        return usuarios.isEmpty() ? null : usuarios.getFirst();
    }

    // cadastrar chamado no banco este metodo grava assunto descricao e usuario e retorna o identificador
    public Long inserir(Long idUsuario, String assunto, String descricao) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO public."CHAMADOS" (id_usuario, assunto, descricao)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, idUsuario, assunto, descricao);
    }

    // listar chamados do titular no banco este metodo consulta os pedidos do usuario do mais recente ao mais antigo
    public List<ChamadoDTO> listarDoUsuario(Long idUsuario) {
        return jdbcTemplate.query(SQL_CHAMADOS + " WHERE c.id_usuario = ? ORDER BY c.created_at DESC, c.id DESC",
                (rs, linha) -> montarChamado(rs), idUsuario);
    }

    // listar chamados no banco administrativo este metodo consulta todos os pedidos ou filtra pela situacao
    public List<ChamadoDTO> listarParaAdmin(String status) {
        // listar chamados sem filtro consulta todas as situacoes quando nenhum filtro foi informado
        if (status.isEmpty()) {
            return jdbcTemplate.query(SQL_CHAMADOS + " ORDER BY c.created_at DESC, c.id DESC",
                    (rs, linha) -> montarChamado(rs));
        }
        return jdbcTemplate.query(SQL_CHAMADOS + " WHERE c.status = ? ORDER BY c.created_at DESC, c.id DESC",
                (rs, linha) -> montarChamado(rs), status);
    }

    // buscar chamado do titular este metodo consulta o pedido sem reservar o registro para alteracao
    public ChamadoDTO buscarDoUsuario(Long id, Long idUsuario) {
        return buscarDoUsuario(id, idUsuario, false);
    }

    // buscar chamado do titular com reserva este metodo confere a autoria e pode proteger o registro durante a gravacao
    public ChamadoDTO buscarDoUsuario(Long id, Long idUsuario, boolean bloquear) {
        String sql = SQL_CHAMADOS + " WHERE c.id = ? AND c.id_usuario = ?";
        // reservar chamado do usuario bloqueia alteracoes concorrentes quando a operacao exige exclusividade
        if (bloquear) sql += " FOR UPDATE OF c";
        List<ChamadoDTO> chamados = jdbcTemplate.query(sql,
                (rs, linha) -> montarChamado(rs), id, idUsuario);
        return chamados.isEmpty() ? null : chamados.getFirst();
    }

    // buscar chamado por identificador este metodo consulta o pedido e pode reserva lo para alteracao
    public ChamadoDTO buscarPorId(Long id, boolean bloquear) {
        String sql = SQL_CHAMADOS + " WHERE c.id = ?";
        // reservar chamado pelo codigo bloqueia alteracoes concorrentes quando solicitado
        if (bloquear) sql += " FOR UPDATE OF c";
        List<ChamadoDTO> chamados = jdbcTemplate.query(sql, (rs, linha) -> montarChamado(rs), id);
        return chamados.isEmpty() ? null : chamados.getFirst();
    }

    // listar respostas do chamado este metodo consulta as mensagens em ordem de envio com os nomes dos autores
    public List<ChamadoRespostaDTO> listarRespostas(Long idChamado) {
        return jdbcTemplate.query("""
                SELECT r.*, COALESCE(u.nome, CASE WHEN r.tipo_autor = 'admin'
                    THEN 'Administrador removido' ELSE 'Usuário removido' END) AS nome_autor
                FROM public."CHAMADO_RESPOSTAS" r
                LEFT JOIN public."USUARIOS" u ON u.id = COALESCE(r.id_admin_usuario, r.id_usuario)
                WHERE r.id_chamado = ? ORDER BY r.created_at, r.id
                """, (rs, linha) -> {
            ChamadoRespostaDTO resposta = new ChamadoRespostaDTO();
            resposta.setId(rs.getLong("id"));
            resposta.setIdChamado(rs.getLong("id_chamado"));
            resposta.setIdAdminUsuario(rs.getObject("id_admin_usuario", Long.class));
            resposta.setIdUsuario(rs.getObject("id_usuario", Long.class));
            resposta.setTipoAutor(rs.getString("tipo_autor"));
            resposta.setNomeAutor(rs.getString("nome_autor"));
            resposta.setResposta(rs.getString("resposta"));
            resposta.setCreatedAt(lerData(rs, "created_at"));
            return resposta;
        }, idChamado);
    }

    // gravar resposta administrativa este metodo salva a mensagem e marca o chamado como respondido
    public void inserirResposta(Long idChamado, Long idAdmin, String resposta) {
        jdbcTemplate.update("""
                INSERT INTO public."CHAMADO_RESPOSTAS" (id_chamado, id_admin_usuario, resposta, tipo_autor, created_at)
                VALUES (?, ?, ?, 'admin', clock_timestamp())
                """, idChamado, idAdmin, resposta);
        jdbcTemplate.update("UPDATE public.\"CHAMADOS\" SET status = 'respondido' WHERE id = ?", idChamado);
    }

    // gravar mensagem do titular este metodo salva a mensagem e marca o chamado como aberto
    public void inserirMensagemUsuario(Long idChamado, Long idUsuario, String mensagem) {
        jdbcTemplate.update("""
                INSERT INTO public."CHAMADO_RESPOSTAS" (id_chamado, id_usuario, resposta, tipo_autor, created_at)
                VALUES (?, ?, ?, 'usuario', clock_timestamp())
                """, idChamado, idUsuario, mensagem);
        jdbcTemplate.update("UPDATE public.\"CHAMADOS\" SET status = 'aberto' WHERE id = ?", idChamado);
    }

    // concluir chamado no banco este metodo grava a situacao a data e o administrador responsavel
    public void concluir(Long idChamado, Long idAdmin) {
        jdbcTemplate.update("""
                UPDATE public."CHAMADOS"
                SET status = 'concluido', concluido_em = clock_timestamp(), id_admin_conclusao = ?
                WHERE id = ?
                """, idAdmin, idChamado);
    }

    // montar dados do chamado este metodo organiza o pedido consultado com os dados do titular
    private ChamadoDTO montarChamado(ResultSet rs) throws SQLException {
        ChamadoDTO chamado = new ChamadoDTO();
        chamado.setId(rs.getLong("id"));
        chamado.setIdUsuario(rs.getLong("id_usuario"));
        chamado.setAssunto(rs.getString("assunto"));
        chamado.setDescricao(rs.getString("descricao"));
        chamado.setStatus(rs.getString("status"));
        chamado.setCreatedAt(lerData(rs, "created_at"));
        chamado.setConcluidoEm(lerData(rs, "concluido_em"));
        chamado.setIdAdminConclusao(rs.getObject("id_admin_conclusao", Long.class));
        chamado.setNomeUsuario(rs.getString("nome_usuario"));
        chamado.setEmailUsuario(rs.getString("email_usuario"));
        return chamado;
    }

    // ler data do chamado este metodo ajusta o horario consultado para sao paulo
    private OffsetDateTime lerData(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime data = rs.getObject(coluna, OffsetDateTime.class);
        return data == null ? null : data.atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();
    }
}
