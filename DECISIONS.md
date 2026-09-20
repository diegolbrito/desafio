# DECISIONS.md — $$$$$$$$ Credit Engine

Registro de decisões importantes tomadas durante o projeto: arquiteturais, de segurança, de
processo, com o contexto e a justificativa por trás de cada uma. Diferente do [`SPEC.md`](SPEC.md)
("Premissas adotadas", decisões de negócio/domínio) e do [`PROGRESS.md`](PROGRESS.md) (histórico
técnico de implementação), este arquivo reúne decisões de mais alto nível, cujo raciocínio vale a
pena manter explícito mesmo depois que o código já reflete o resultado.

## 1. Segurança: sem autenticação, autorização ou IdP próprio

Optei conscientemente por não implementar autenticação, autorização ou um IdP no projeto. O
objetivo do desafio é demonstrar o domínio da regra de negócio e da arquitetura do serviço, e a
camada de identidade, em um cenário real, seria delegada a um provedor corporativo já existente,
não reimplementada dentro da aplicação.

Como o ambiente roda isolado, sem exposição externa e sem dados reais, o controle não se
justificaria tecnicamente.

Ainda assim, a API foi mantida stateless e desacoplada, de modo que a validação de token via
OAuth2/OIDC possa ser adicionada como filtro na borda sem impacto na camada de domínio.
