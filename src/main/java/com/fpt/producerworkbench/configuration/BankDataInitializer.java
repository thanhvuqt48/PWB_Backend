package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.entity.Bank;
import com.fpt.producerworkbench.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BankDataInitializer {

    private final BankRepository bankRepository;

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.primary.driver-class-name",
            havingValue = "com.mysql.cj.jdbc.Driver")
    @Order(0) // Run first, before other initializers
    ApplicationRunner initializeBanks() {
        return args -> {
            if (bankRepository.count() > 0) {
                log.info("Banks already initialized. Skipping...");
                return;
            }

            log.info("Initializing banks data...");
            List<Bank> banks = createBankList();
            bankRepository.saveAll(banks);
            log.info("Successfully initialized {} banks.", banks.size());
        };
    }

    private List<Bank> createBankList() {
        List<Bank> banks = new ArrayList<>();
        
        banks.add(Bank.builder().code("ICB").name("Ngân hàng TMCP Công thương Việt Nam").shortName("VietinBank").bin("970415").logoUrl("https://cdn.vietqr.io/img/ICB.png").transferSupported(true).lookupSupported(true).swiftCode("ICBVVNVX").build());
        banks.add(Bank.builder().code("VCB").name("Ngân hàng TMCP Ngoại Thương Việt Nam").shortName("Vietcombank").bin("970436").logoUrl("https://cdn.vietqr.io/img/VCB.png").transferSupported(true).lookupSupported(true).swiftCode("BFTVVNVX").build());
        banks.add(Bank.builder().code("BIDV").name("Ngân hàng TMCP Đầu tư và Phát triển Việt Nam").shortName("BIDV").bin("970418").logoUrl("https://cdn.vietqr.io/img/BIDV.png").transferSupported(true).lookupSupported(true).swiftCode("BIDVVNVX").build());
        banks.add(Bank.builder().code("VBA").name("Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam").shortName("Agribank").bin("970405").logoUrl("https://cdn.vietqr.io/img/VBA.png").transferSupported(true).lookupSupported(true).swiftCode("VBAAVNVX").build());
        banks.add(Bank.builder().code("OCB").name("Ngân hàng TMCP Phương Đông").shortName("OCB").bin("970448").logoUrl("https://cdn.vietqr.io/img/OCB.png").transferSupported(true).lookupSupported(true).swiftCode("ORCOVNVX").build());
        banks.add(Bank.builder().code("MB").name("Ngân hàng TMCP Quân đội").shortName("MBBank").bin("970422").logoUrl("https://cdn.vietqr.io/img/MB.png").transferSupported(true).lookupSupported(true).swiftCode("MSCBVNVX").build());
        banks.add(Bank.builder().code("TCB").name("Ngân hàng TMCP Kỹ thương Việt Nam").shortName("Techcombank").bin("970407").logoUrl("https://cdn.vietqr.io/img/TCB.png").transferSupported(true).lookupSupported(true).swiftCode("VTCBVNVX").build());
        banks.add(Bank.builder().code("ACB").name("Ngân hàng TMCP Á Châu").shortName("ACB").bin("970416").logoUrl("https://cdn.vietqr.io/img/ACB.png").transferSupported(true).lookupSupported(true).swiftCode("ASCBVNVX").build());
        banks.add(Bank.builder().code("VPB").name("Ngân hàng TMCP Việt Nam Thịnh Vượng").shortName("VPBank").bin("970432").logoUrl("https://cdn.vietqr.io/img/VPB.png").transferSupported(true).lookupSupported(true).swiftCode("VPBKVNVX").build());
        banks.add(Bank.builder().code("TPB").name("Ngân hàng TMCP Tiên Phong").shortName("TPBank").bin("970423").logoUrl("https://cdn.vietqr.io/img/TPB.png").transferSupported(true).lookupSupported(true).swiftCode("TPBVVNVX").build());
        banks.add(Bank.builder().code("STB").name("Ngân hàng TMCP Sài Gòn Thương Tín").shortName("Sacombank").bin("970403").logoUrl("https://cdn.vietqr.io/img/STB.png").transferSupported(true).lookupSupported(true).swiftCode("SGTTVNVX").build());
        banks.add(Bank.builder().code("HDB").name("Ngân hàng TMCP Phát triển Thành phố Hồ Chí Minh").shortName("HDBank").bin("970437").logoUrl("https://cdn.vietqr.io/img/HDB.png").transferSupported(true).lookupSupported(true).swiftCode("HDBCVNVX").build());
        banks.add(Bank.builder().code("VCCB").name("Ngân hàng TMCP Bản Việt").shortName("VietCapitalBank").bin("970454").logoUrl("https://cdn.vietqr.io/img/VCCB.png").transferSupported(true).lookupSupported(true).swiftCode("VCBCVNVX").build());
        banks.add(Bank.builder().code("SCB").name("Ngân hàng TMCP Sài Gòn").shortName("SCB").bin("970429").logoUrl("https://cdn.vietqr.io/img/SCB.png").transferSupported(true).lookupSupported(true).swiftCode("SACLVNVX").build());
        banks.add(Bank.builder().code("VIB").name("Ngân hàng TMCP Quốc tế Việt Nam").shortName("VIB").bin("970441").logoUrl("https://cdn.vietqr.io/img/VIB.png").transferSupported(true).lookupSupported(true).swiftCode("VNIBVNVX").build());
        banks.add(Bank.builder().code("SHB").name("Ngân hàng TMCP Sài Gòn - Hà Nội").shortName("SHB").bin("970443").logoUrl("https://cdn.vietqr.io/img/SHB.png").transferSupported(true).lookupSupported(true).swiftCode("SHBAVNVX").build());
        banks.add(Bank.builder().code("EIB").name("Ngân hàng TMCP Xuất Nhập khẩu Việt Nam").shortName("Eximbank").bin("970431").logoUrl("https://cdn.vietqr.io/img/EIB.png").transferSupported(true).lookupSupported(true).swiftCode("EBVIVNVX").build());
        banks.add(Bank.builder().code("MSB").name("Ngân hàng TMCP Hàng Hải Việt Nam").shortName("MSB").bin("970426").logoUrl("https://cdn.vietqr.io/img/MSB.png").transferSupported(true).lookupSupported(true).swiftCode("MCOBVNVX").build());
        banks.add(Bank.builder().code("CAKE").name("TMCP Việt Nam Thịnh Vượng - Ngân hàng số CAKE by VPBank").shortName("CAKE").bin("546034").logoUrl("https://cdn.vietqr.io/img/CAKE.png").transferSupported(true).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("Ubank").name("TMCP Việt Nam Thịnh Vượng - Ngân hàng số Ubank by VPBank").shortName("Ubank").bin("546035").logoUrl("https://cdn.vietqr.io/img/UBANK.png").transferSupported(true).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("VTLMONEY").name("Tổng Công ty Dịch vụ số Viettel - Chi nhánh tập đoàn công nghiệp viễn thông Quân Đội").shortName("ViettelMoney").bin("971005").logoUrl("https://cdn.vietqr.io/img/VIETTELMONEY.png").transferSupported(false).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("TIMO").name("Ngân hàng số Timo by Ban Viet Bank (Timo by Ban Viet Bank)").shortName("Timo").bin("963388").logoUrl("https://vietqr.net/portal-service/resources/icons/TIMO.png").transferSupported(true).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("VNPTMONEY").name("VNPT Money").shortName("VNPTMoney").bin("971011").logoUrl("https://cdn.vietqr.io/img/VNPTMONEY.png").transferSupported(false).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("SGICB").name("Ngân hàng TMCP Sài Gòn Công Thương").shortName("SaigonBank").bin("970400").logoUrl("https://cdn.vietqr.io/img/SGICB.png").transferSupported(true).lookupSupported(true).swiftCode("SBITVNVX").build());
        banks.add(Bank.builder().code("BAB").name("Ngân hàng TMCP Bắc Á").shortName("BacABank").bin("970409").logoUrl("https://cdn.vietqr.io/img/BAB.png").transferSupported(true).lookupSupported(true).swiftCode("NASCVNVX").build());
        banks.add(Bank.builder().code("momo").name("CTCP Dịch Vụ Di Động Trực Tuyến").shortName("MoMo").bin("971025").logoUrl("https://cdn.vietqr.io/img/momo.png").transferSupported(true).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("PVDB").name("Ngân hàng TMCP Đại Chúng Việt Nam Ngân hàng số").shortName("PVcomBank Pay").bin("971133").logoUrl("https://cdn.vietqr.io/img/PVCB.png").transferSupported(true).lookupSupported(true).swiftCode("WBVNVNVX").build());
        banks.add(Bank.builder().code("PVCB").name("Ngân hàng TMCP Đại Chúng Việt Nam").shortName("PVcomBank").bin("970412").logoUrl("https://cdn.vietqr.io/img/PVCB.png").transferSupported(true).lookupSupported(true).swiftCode("WBVNVNVX").build());
        banks.add(Bank.builder().code("MBV").name("Ngân hàng TNHH MTV Việt Nam Hiện Đại").shortName("MBV").bin("970414").logoUrl("https://cdn.vietqr.io/img/MBV.png").transferSupported(true).lookupSupported(true).swiftCode("OCBKUS3M").build());
        banks.add(Bank.builder().code("NCB").name("Ngân hàng TMCP Quốc Dân").shortName("NCB").bin("970419").logoUrl("https://cdn.vietqr.io/img/NCB.png").transferSupported(true).lookupSupported(true).swiftCode("NVBAVNVX").build());
        banks.add(Bank.builder().code("SHBVN").name("Ngân hàng TNHH MTV Shinhan Việt Nam").shortName("ShinhanBank").bin("970424").logoUrl("https://cdn.vietqr.io/img/SHBVN.png").transferSupported(true).lookupSupported(true).swiftCode("SHBKVNVX").build());
        banks.add(Bank.builder().code("ABB").name("Ngân hàng TMCP An Bình").shortName("ABBANK").bin("970425").logoUrl("https://cdn.vietqr.io/img/ABB.png").transferSupported(true).lookupSupported(true).swiftCode("ABBKVNVX").build());
        banks.add(Bank.builder().code("VAB").name("Ngân hàng TMCP Việt Á").shortName("VietABank").bin("970427").logoUrl("https://cdn.vietqr.io/img/VAB.png").transferSupported(true).lookupSupported(true).swiftCode("VNACVNVX").build());
        banks.add(Bank.builder().code("NAB").name("Ngân hàng TMCP Nam Á").shortName("NamABank").bin("970428").logoUrl("https://cdn.vietqr.io/img/NAB.png").transferSupported(true).lookupSupported(true).swiftCode("NAMAVNVX").build());
        banks.add(Bank.builder().code("PGB").name("Ngân hàng TMCP Thịnh vượng và Phát triển").shortName("PGBank").bin("970430").logoUrl("https://cdn.vietqr.io/img/PGB.png").transferSupported(true).lookupSupported(true).swiftCode("PGBLVNVX").build());
        banks.add(Bank.builder().code("VIETBANK").name("Ngân hàng TMCP Việt Nam Thương Tín").shortName("VietBank").bin("970433").logoUrl("https://cdn.vietqr.io/img/VIETBANK.png").transferSupported(true).lookupSupported(true).swiftCode("VNTTVNVX").build());
        banks.add(Bank.builder().code("BVB").name("Ngân hàng TMCP Bảo Việt").shortName("BaoVietBank").bin("970438").logoUrl("https://cdn.vietqr.io/img/BVB.png").transferSupported(true).lookupSupported(true).swiftCode("BVBVVNVX").build());
        banks.add(Bank.builder().code("SEAB").name("Ngân hàng TMCP Đông Nam Á").shortName("SeABank").bin("970440").logoUrl("https://cdn.vietqr.io/img/SEAB.png").transferSupported(true).lookupSupported(true).swiftCode("SEAVVNVX").build());
        banks.add(Bank.builder().code("COOPBANK").name("Ngân hàng Hợp tác xã Việt Nam").shortName("COOPBANK").bin("970446").logoUrl("https://cdn.vietqr.io/img/COOPBANK.png").transferSupported(true).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("LPB").name("Ngân hàng TMCP Lộc Phát Việt Nam").shortName("LPBank").bin("970449").logoUrl("https://cdn.vietqr.io/img/LPB.png").transferSupported(true).lookupSupported(true).swiftCode("LVBKVNVX").build());
        banks.add(Bank.builder().code("KLB").name("Ngân hàng TMCP Kiên Long").shortName("KienLongBank").bin("970452").logoUrl("https://cdn.vietqr.io/img/KLB.png").transferSupported(true).lookupSupported(true).swiftCode("KLBKVNVX").build());
        banks.add(Bank.builder().code("KBank").name("Ngân hàng Đại chúng TNHH Kasikornbank").shortName("KBank").bin("668888").logoUrl("https://cdn.vietqr.io/img/KBANK.png").transferSupported(true).lookupSupported(true).swiftCode("KASIVNVX").build());
        banks.add(Bank.builder().code("MAFC").name("Công ty Tài chính TNHH MTV Mirae Asset (Việt Nam) ").shortName("MAFC").bin("977777").logoUrl("https://cdn.vietqr.io/img/MAFC.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("HLBVN").name("Ngân hàng TNHH MTV Hong Leong Việt Nam").shortName("HongLeong").bin("970442").logoUrl("https://cdn.vietqr.io/img/HLBVN.png").transferSupported(false).lookupSupported(true).swiftCode("HLBBVNVX").build());
        banks.add(Bank.builder().code("KEBHANAHN").name("Ngân hàng KEB Hana – Chi nhánh Hà Nội").shortName("KEBHANAHN").bin("970467").logoUrl("https://cdn.vietqr.io/img/KEBHANAHN.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("KEBHANAHCM").name("Ngân hàng KEB Hana – Chi nhánh Thành phố Hồ Chí Minh").shortName("KEBHanaHCM").bin("970466").logoUrl("https://cdn.vietqr.io/img/KEBHANAHCM.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("CITIBANK").name("Ngân hàng Citibank, N.A. - Chi nhánh Hà Nội").shortName("Citibank").bin("533948").logoUrl("https://cdn.vietqr.io/img/CITIBANK.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("CBB").name("Ngân hàng Thương mại TNHH MTV Xây dựng Việt Nam").shortName("CBBank").bin("970444").logoUrl("https://cdn.vietqr.io/img/CBB.png").transferSupported(false).lookupSupported(true).swiftCode("GTBAVNVX").build());
        banks.add(Bank.builder().code("CIMB").name("Ngân hàng TNHH MTV CIMB Việt Nam").shortName("CIMB").bin("422589").logoUrl("https://cdn.vietqr.io/img/CIMB.png").transferSupported(true).lookupSupported(true).swiftCode("CIBBVNVN").build());
        banks.add(Bank.builder().code("DBS").name("DBS Bank Ltd - Chi nhánh Thành phố Hồ Chí Minh").shortName("DBSBank").bin("796500").logoUrl("https://cdn.vietqr.io/img/DBS.png").transferSupported(false).lookupSupported(false).swiftCode("DBSSVNVX").build());
        banks.add(Bank.builder().code("Vikki").name("Ngân hàng TNHH MTV Số Vikki").shortName("Vikki").bin("970406").logoUrl("https://cdn.vietqr.io/img/Vikki.png").transferSupported(false).lookupSupported(true).swiftCode("EACBVNVX").build());
        banks.add(Bank.builder().code("VBSP").name("Ngân hàng Chính sách Xã hội").shortName("VBSP").bin("999888").logoUrl("https://cdn.vietqr.io/img/VBSP.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("GPB").name("Ngân hàng Thương mại TNHH MTV Dầu Khí Toàn Cầu").shortName("GPBank").bin("970408").logoUrl("https://cdn.vietqr.io/img/GPB.png").transferSupported(false).lookupSupported(true).swiftCode("GBNKVNVX").build());
        banks.add(Bank.builder().code("KBHCM").name("Ngân hàng Kookmin - Chi nhánh Thành phố Hồ Chí Minh").shortName("KookminHCM").bin("970463").logoUrl("https://cdn.vietqr.io/img/KBHCM.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("KBHN").name("Ngân hàng Kookmin - Chi nhánh Hà Nội").shortName("KookminHN").bin("970462").logoUrl("https://cdn.vietqr.io/img/KBHN.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("WVN").name("Ngân hàng TNHH MTV Woori Việt Nam").shortName("Woori").bin("970457").logoUrl("https://cdn.vietqr.io/img/WVN.png").transferSupported(true).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("VRB").name("Ngân hàng Liên doanh Việt - Nga").shortName("VRB").bin("970421").logoUrl("https://cdn.vietqr.io/img/VRB.png").transferSupported(false).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("HSBC").name("Ngân hàng TNHH MTV HSBC (Việt Nam)").shortName("HSBC").bin("458761").logoUrl("https://cdn.vietqr.io/img/HSBC.png").transferSupported(false).lookupSupported(true).swiftCode("HSBCVNVX").build());
        banks.add(Bank.builder().code("IBK - HN").name("Ngân hàng Công nghiệp Hàn Quốc - Chi nhánh Hà Nội").shortName("IBKHN").bin("970455").logoUrl("https://cdn.vietqr.io/img/IBK.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("IBK - HCM").name("Ngân hàng Công nghiệp Hàn Quốc - Chi nhánh TP. Hồ Chí Minh").shortName("IBKHCM").bin("970456").logoUrl("https://cdn.vietqr.io/img/IBK.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("IVB").name("Ngân hàng TNHH Indovina").shortName("IndovinaBank").bin("970434").logoUrl("https://cdn.vietqr.io/img/IVB.png").transferSupported(false).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("UOB").name("Ngân hàng United Overseas - Chi nhánh TP. Hồ Chí Minh").shortName("UnitedOverseas").bin("970458").logoUrl("https://cdn.vietqr.io/img/UOB.png").transferSupported(false).lookupSupported(true).swiftCode(null).build());
        banks.add(Bank.builder().code("NHB HN").name("Ngân hàng Nonghyup - Chi nhánh Hà Nội").shortName("Nonghyup").bin("801011").logoUrl("https://cdn.vietqr.io/img/NHB.png").transferSupported(false).lookupSupported(false).swiftCode(null).build());
        banks.add(Bank.builder().code("SCVN").name("Ngân hàng TNHH MTV Standard Chartered Bank Việt Nam").shortName("StandardChartered").bin("970410").logoUrl("https://cdn.vietqr.io/img/SCVN.png").transferSupported(false).lookupSupported(true).swiftCode("SCBLVNVX").build());
        banks.add(Bank.builder().code("PBVN").name("Ngân hàng TNHH MTV Public Việt Nam").shortName("PublicBank").bin("970439").logoUrl("https://cdn.vietqr.io/img/PBVN.png").transferSupported(false).lookupSupported(true).swiftCode("VIDPVNVX").build());
        
        return banks;
    }
}

