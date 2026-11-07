// horas de estudo organizam a disponibilidade diaria dos alunos
package com.example.CampusLink.service;

import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

// servico de disponibilidade esta classe consulta os dias livres e cria ou atualiza suas horas
@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    @Autowired
    private UsuarioRepository UsuarioRepository;


    private static final Logger logger = LoggerFactory.getLogger(DisponibilidadeService.class);
    private final DisponibilidadeRepository disponibilidadeRepository;


    // salvar horas disponiveis este metodo cria ou atualiza a disponibilidade do aluno no dia informado
    public Disponibilidade salvarDisponibilidade(Long idAluno, LocalDate data, Integer horasDisponiveis) {

        // salvar disponibilidade tenta criar ou atualizar as horas disponiveis do aluno na data
        try {logger.debug("Buscando disponibilidade existente. alunoId={} data={}", idAluno, data);
            // registro de detalhes confere se esta habilitado ao salvar horas disponiveis
            if (logger.isDebugEnabled()) {
                // gravar historico ao salvar horas disponiveis tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "DEBUG", DisponibilidadeService.class.getName(), "salvarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Buscando disponibilidade existente. alunoId={} data={}", new Object[]{idAluno, data}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }


            Disponibilidade disponibilidade =
                    disponibilidadeRepository
                            .findByIdAlunoAndData(idAluno, data)
                            .orElseGet(() -> new Disponibilidade(idAluno, data, horasDisponiveis));


            disponibilidade.setHorasDisponiveis(horasDisponiveis);


            Disponibilidade disponibilidadeSalva = disponibilidadeRepository.save(disponibilidade);

            logger.info("Disponibilidade salva. id={} alunoId={} data={} horas={}", disponibilidadeSalva.getId(), idAluno, data, horasDisponiveis);
            // registro de informacoes confere se esta habilitado ao salvar horas disponiveis
            if (logger.isInfoEnabled()) {
                // gravar historico ao salvar horas disponiveis tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", DisponibilidadeService.class.getName(), "salvarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Disponibilidade salva. id={} alunoId={} data={} horas={}", new Object[]{disponibilidadeSalva.getId(), idAluno, data, horasDisponiveis}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return disponibilidadeSalva;

        } catch (DataAccessException e) {
            logger.error("Erro ao salvar disponibilidade. alunoId={} data={}", idAluno, data, e);
            // registro de erros confere se esta habilitado ao salvar horas disponiveis
            if (logger.isErrorEnabled()) {
                // gravar historico ao salvar horas disponiveis tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DisponibilidadeService.class.getName(), "salvarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Erro ao salvar disponibilidade. alunoId={} data={}", new Object[]{idAluno, data}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }


    // consultar dias disponiveis este metodo lista as horas livres do aluno em ordem de data
    public List<Disponibilidade> listarPorAluno(Long idAluno) {

        // listar disponibilidade consulta as datas do aluno e registra o resultado
        try {
            List<Disponibilidade> disponibilidades = disponibilidadeRepository.findAllByIdAlunoOrderByDataAsc(idAluno);
            logger.debug("Disponibilidades consultadas. alunoId={} quantidade={}", idAluno, disponibilidades.size());
            // registro de detalhes confere se esta habilitado ao consultar dias disponiveis
            if (logger.isDebugEnabled()) {
                // gravar historico ao consultar dias disponiveis tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "DEBUG", DisponibilidadeService.class.getName(), "listarPorAluno", null,
                            MessageFormatter.arrayFormat("Disponibilidades consultadas. alunoId={} quantidade={}", new Object[]{idAluno, disponibilidades.size()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return disponibilidades;

        } catch (DataAccessException e) {
            logger.error("Erro ao consultar disponibilidades. alunoId={}", idAluno, e);
            // registro de erros confere se esta habilitado ao consultar dias disponiveis
            if (logger.isErrorEnabled()) {
                // gravar historico ao consultar dias disponiveis tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DisponibilidadeService.class.getName(), "listarPorAluno", null,
                            MessageFormatter.arrayFormat("Erro ao consultar disponibilidades. alunoId={}", new Object[]{idAluno}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }
}