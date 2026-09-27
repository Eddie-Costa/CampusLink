package com.example.CampusLink.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusLinkOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusLink - Integrações externas")
                        .version("1.0.0")
                        .description("""
                                O CampusLink integra serviços externos para apoiar a seleção de
                                materiais acadêmicos e a verificação de acesso à plataforma.

                                ### Integrações de pesquisa
                                - **Google Books API v1:** busca informações bibliográficas em
                                  `GET https://www.googleapis.com/books/v1/volumes`.
                                  O backend solicita até 10 resultados e retorna título, autores,
                                  editora, ano, ISBN, miniatura e link do livro.
                                - **YouTube Data API v3:** pesquisa até 10 vídeos em
                                  `GET https://www.googleapis.com/youtube/v3/search` e consulta
                                  `GET https://www.googleapis.com/youtube/v3/videos` para obter a duração.
                                  O retorno contém identificador, título, canal, miniatura e duração.

                                O navegador chama as rotas do CampusLink listadas abaixo.
                                Os controllers verificam a sessão, os services consultam as APIs externas
                                com `RestClient` e os DTOs organizam a resposta JSON enviada à interface.
                                Na tela da turma, selecionar um resultado preenche campos do formulário
                                de conteúdo. A busca, por si só, não salva o material na turma.

                                ### Como executar uma busca no Swagger
                                1. Entre no CampusLink e conclua a verificação por e-mail.
                                2. Abra esta página no mesmo navegador e na mesma origem da aplicação
                                   (mesmo protocolo, domínio e porta).
                                3. Abra uma operação, clique em **Try it out**, preencha `q` e clique em **Execute**.
                                4. Confira os dados em **Server response / Response body**.
                                   Os exemplos desta documentação são ilustrativos, não consultas já executadas.

                                ### Sessão e configuração
                                As buscas verificam o atributo `usuarioLogado` da sessão HTTP.
                                O navegador utiliza o cookie de sessão, normalmente `JSESSIONID`.
                                Nesta versão, sem usuário logado, o retorno é **HTTP 200 com `[]`**,
                                sem chamada à API externa. Os dois controllers verificam a presença
                                do usuário na sessão, sem uma restrição adicional por perfil.

                                As credenciais do Google são configuradas somente no backend:
                                `GOOGLE_BOOKS_API_KEY` alimenta `googlebooks.api.key` e
                                `YOUTUBE_API_KEY` alimenta `youtube.api.key`.
                                Os services acrescentam a chave ao parâmetro `key` das chamadas externas.
                                Não é necessário informar essas chaves no Swagger.

                                ### Brevo: integração complementar de e-mail
                                O `emailService` utiliza `JavaMailSender` para enviar códigos de verificação
                                de login, cadastro e reenvio pelo **SMTP do Brevo**.
                                A configuração atual usa `smtp-relay.brevo.com`, porta `2525`, autenticação
                                e STARTTLS obrigatório, com `BREVO_USERNAME` e `BREVO_PASSWORD`.
                                O envio é assíncrono e faz parte desses fluxos da aplicação.
                                Nesta versão, o Brevo é consumido por SMTP; não existe uma rota REST
                                de envio Brevo para executar nesta documentação.
                                """));
    }
}