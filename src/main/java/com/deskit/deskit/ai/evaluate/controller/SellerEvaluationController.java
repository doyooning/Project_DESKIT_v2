package com.deskit.deskit.ai.evaluate.controller;

import com.deskit.deskit.account.entity.SellerRegister;
import com.deskit.deskit.account.repository.SellerRegisterRepository;
import com.deskit.deskit.ai.evaluate.entity.AiEvaluation;
import com.deskit.deskit.ai.evaluate.repository.AiEvalRepository;
import com.deskit.deskit.ai.evaluate.service.SellerPlanEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class SellerEvaluationController {

    private final SellerRegisterRepository sellerRegisterRepository;
    private final SellerPlanEvaluationService sellerPlanEvaluationService;
    private final AiEvalRepository aiEvalRepository;

    // 판매자 회원가입 화면
    @GetMapping("/seller/register")
    public String showSellerRegisterForm() {
        return "seller-register";
    }

    // AI 심사 처리
    @PostMapping("/seller/register")
    public String submitSellerRegister(@RequestParam("sellerId") Long sellerId,
                                       @RequestParam("companyName") String companyName,
                                       @RequestParam("description") String description,
                                       @RequestParam("planFile") MultipartFile planFile) throws IOException {

        SellerRegister registerEntity = new SellerRegister();
        registerEntity.setSellerId(sellerId);
        registerEntity.setCompanyName(companyName);
        registerEntity.setDescription(description);
        registerEntity.setPlanFile(planFile.getBytes());

        // 회원가입 정보 저장 및 심사
        SellerRegister saved = sellerRegisterRepository.save(registerEntity);
        sellerPlanEvaluationService.evaluateAndSave(saved);

        return "redirect:/admin/evaluations";
    }

    // 관리자 심사 화면
    @GetMapping("/admin/evaluations")
    public String showAdminEvaluations(Model model) {
        List<AiEvaluation> evaluations = aiEvalRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("evaluations", evaluations);
        return "admin-evaluations";
    }
}
