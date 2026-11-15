# Oakkar VPN (Android)

Key ထည့်သုံးရသော VPN app။ Key ကို paste လုပ်ပြီး Home မှ ချိတ်ဆက်ပါ။

## ပါဝင်သည်များ
- Home: ချိတ်/ဖြတ် ခလုတ်၊ ကြာချိန်၊ speed၊ Data သုံးစွဲမှု (ဒီနေ့ / ဒီလ / စုစုပေါင်း)
- Servers: key စာရင်း၊ ရွေးချယ်၊ ဖျက်
- Key ထည့်ရန်: vmess, vless, trojan, ss (Outline ပါ), ssconf (Outline dynamic key), base64 စာသား၊ Outline JSON
- Xray core + Android VpnService (tun)

## APK ထုတ်နည်း (GitHub Actions)
1. ဤ folder တစ်ခုလုံးကို GitHub repository သို့ upload လုပ်ပါ (`main` branch)။
2. Repository ၏ **Actions** tab → **Build APK** → **Run workflow**။
3. ပြီးလျှင် `oakkar-vpn-apk` artifact ကို ဒေါင်းပြီး APK ကို ဖုန်းတွင် install လုပ်ပါ။
