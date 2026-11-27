const LOGO_URL = 'https://lh3.googleusercontent.com/d/1_8pLIvg1rVkou8ak4ENA3JLtuIFcuRA7';

function verificationEmailHtml(code) {
  if (typeof code !== 'string' || !/^\d{6}$/.test(code)) throw new Error('Invalid code');
  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <style>
    body { margin: 0; padding: 0; -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
    img { border: 0; outline: none; text-decoration: none; }
    @media only screen and (max-width: 480px) {
      .outer { padding: 20px 12px !important; }
      .card { padding: 24px 16px !important; border-radius: 12px !important; }
      .brand { font-size: 24px !important; }
      .code { font-size: 28px !important; letter-spacing: 4px !important; padding: 14px 6px !important; }
    }
    @media only screen and (max-width: 360px) {
      .code { font-size: 24px !important; letter-spacing: 2px !important; padding: 12px 4px !important; }
    }
  </style>
</head>
<body style="margin: 0; padding: 0; background-color: #121212;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color: #121212;">
    <tr><td align="center" class="outer" style="padding: 40px 20px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width: 480px; width: 100%;">
        <tr><td class="card" style="background-color: #1E1E1E; padding: 32px; border-radius: 16px; border: 1px solid #2A2A2A; font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #E0E0E0;">
          <div style="text-align: center; margin-bottom: 24px;">
            <div style="width: 56px; height: 49px; padding-top: 10px; margin: 0 auto 12px auto; border-radius: 14px; background-color: #20222A;">
              <img src="${LOGO_URL}" alt="IronVault" width="29" height="40" style="display: block; margin: 0 auto; border: 0;">
            </div>
            <h1 class="brand" style="font-size: 28px; margin: 0; font-weight: 800;">
              <span style="color: #FFFFFF;">Iron</span><span style="color: #C97D3E;">Vault</span>
            </h1>
            <p style="color: #9CA3AF; font-size: 13px; margin: 4px 0 0 0; font-weight: 500;">VERIFICACIÓN DE CORREO</p>
          </div>
          <hr style="border: none; border-top: 1px solid #2A2A2A; margin: 20px 0;">
          <p style="color: #FFFFFF; font-size: 15px; font-weight: 600; margin: 0 0 8px 0;">Hola,</p>
          <p style="color: #9CA3AF; font-size: 14px; line-height: 1.6; margin: 0;">Usa el siguiente código de verificación para continuar con tu registro en IronVault:</p>
          <div style="margin: 32px 0;">
            <div class="code" style="display: block; width: 100%; box-sizing: border-box; text-align: center; font-size: 32px; font-weight: 800; background-color: #121212; color: #C97D3E; padding: 16px 8px; border-radius: 12px; letter-spacing: 6px; border: 2px solid #C97D3E;">${code}</div>
            <p style="color: #EF4444; font-size: 12px; margin: 12px 0 0 0; font-weight: 500; text-align: center;">Válido durante 10 minutos desde la solicitud</p>
          </div>
          <p style="color: #6B7280; font-size: 12px; text-align: center; line-height: 1.5; margin: 0;">Si no solicitaste este código, puedes ignorar este mensaje.</p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}
