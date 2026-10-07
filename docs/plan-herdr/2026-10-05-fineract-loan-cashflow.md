# Fineract-inspired Loan & Cashflow Management Multi-Agent Implementation Plan

## Wave 0: Global State & Foundation (Sequential)
**Convergence Goal:** Establish the database schema and core JPA entities without migration version collisions.

### Task 0: Database Schema & Core Entities
- Scope: Medium (Pair)
- Target Service: `backend/`
- Worktree: `.worktrees/task-0`
- Branch: `task-0`

#### Role: Worker-0
**Directive:** Execute the following TDD lifecycle strictly inside `.worktrees/task-0`.

**1. Target Files:**
- Create: `backend/src/main/resources/db/migration/V22__create_loan_and_cashflow_tables.sql`
- Create: `backend/src/main/java/com/ddicg/erp/loan/entity/Loan.java`
- Create: `backend/src/main/java/com/ddicg/erp/loan/entity/Collateral.java`
- Create: `backend/src/main/java/com/ddicg/erp/loan/entity/RepaymentSchedule.java`
- Create: `backend/src/main/java/com/ddicg/erp/loan/repository/LoanRepository.java`
- Create: `backend/src/main/java/com/ddicg/erp/loan/repository/RepaymentScheduleRepository.java`

**2. Failing Test (Step 1):**
```java
// backend/src/test/java/com/ddicg/erp/loan/repository/LoanRepositoryTest.java
package com.ddicg.erp.loan.repository;

import com.ddicg.erp.loan.entity.Loan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class LoanRepositoryTest {
    @Autowired private LoanRepository loanRepository;

    @Test
    void shouldSaveAndRetrieveLoan() {
        Loan loan = new Loan();
        loan.setPrincipal(new BigDecimal("10000.00"));
        loan.setInterestRate(new BigDecimal("5.0"));
        loan.setStatus("ACTIVE");
        loan.setStartDate(LocalDate.now());
        
        Loan saved = loanRepository.save(loan);
        assertThat(saved.getId()).isNotNull();
    }
}
```
*Command to run:* `./mvnw test -Dtest=LoanRepositoryTest` (Expected: FAIL)

**3. Implementation (Step 3):**
```sql
-- backend/src/main/resources/db/migration/V22__create_loan_and_cashflow_tables.sql
CREATE TABLE loans (
    id BIGSERIAL PRIMARY KEY,
    principal DECIMAL(19,4) NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL
);
CREATE TABLE repayment_schedules (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT REFERENCES loans(id),
    due_date DATE NOT NULL,
    principal_amount DECIMAL(19,4) NOT NULL,
    interest_amount DECIMAL(19,4) NOT NULL,
    penalty_amount DECIMAL(19,4) DEFAULT 0.00,
    status VARCHAR(50) NOT NULL
);
CREATE TABLE collaterals (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT REFERENCES loans(id),
    type VARCHAR(100) NOT NULL,
    value DECIMAL(19,4) NOT NULL,
    description TEXT
);
```
```java
// backend/src/main/java/com/ddicg/erp/loan/entity/Loan.java
package com.ddicg.erp.loan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Entity
@Table(name = "loans")
public class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal principal;
    private BigDecimal interestRate;
    private String status;
    private LocalDate startDate;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL)
    private List<RepaymentSchedule> schedules;
}
```
```java
// backend/src/main/java/com/ddicg/erp/loan/entity/RepaymentSchedule.java
package com.ddicg.erp.loan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "repayment_schedules")
public class RepaymentSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private Loan loan;
    
    private LocalDate dueDate;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal penaltyAmount = BigDecimal.ZERO;
    private String status;
}
```
```java
// backend/src/main/java/com/ddicg/erp/loan/entity/Collateral.java
package com.ddicg.erp.loan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "collaterals")
public class Collateral {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    private String type;
    private BigDecimal value;
    private String description;
}
```
```java
// backend/src/main/java/com/ddicg/erp/loan/repository/LoanRepository.java
package com.ddicg.erp.loan.repository;

import com.ddicg.erp.loan.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
}
```
```java
// backend/src/main/java/com/ddicg/erp/loan/repository/RepaymentScheduleRepository.java
package com.ddicg.erp.loan.repository;

import com.ddicg.erp.loan.entity.RepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, Long> {
    List<RepaymentSchedule> findByStatusAndDueDateBefore(String status, LocalDate date);
}
```
*Command to run:* `./mvnw test -Dtest=LoanRepositoryTest` (Expected: PASS)

#### Role: Censor-0
**Directive:** Review diff against `task-0` branch.
- Verify: Flyway script `V22__create_loan_and_cashflow_tables.sql` maps correctly to `Loan`, `Collateral`, and `RepaymentSchedule` entities.
- Verify: `RepaymentScheduleRepository` contains the correct custom query method for fetching by status and date.

---

## Wave 1: Core Business Logic (Parallel)
**Convergence Goal:** Accurate generation of repayment schedules and reliable scheduled penalty triggers.

### Task 1: Repayment Schedule Generator & Loan Service
- Scope: Medium-Small (Solo)
- Target Service: `backend/`
- Worktree: `.worktrees/task-1`
- Branch: `task-1`

#### Role: Worker-1
**Directive:** Execute the following TDD lifecycle strictly inside `.worktrees/task-1`.

**1. Target Files:**
- Create: `backend/src/main/java/com/ddicg/erp/loan/service/RepaymentScheduleGenerator.java`
- Create: `backend/src/main/java/com/ddicg/erp/loan/service/LoanService.java`

**2. Failing Test (Step 1):**
```java
// backend/src/test/java/com/ddicg/erp/loan/service/RepaymentScheduleGeneratorTest.java
package com.ddicg.erp.loan.service;

import com.ddicg.erp.loan.entity.Loan;
import com.ddicg.erp.loan.entity.RepaymentSchedule;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class RepaymentScheduleGeneratorTest {
    @Test
    void shouldGenerateFlatEMISchedule() {
        RepaymentScheduleGenerator generator = new RepaymentScheduleGenerator();
        Loan loan = new Loan();
        loan.setPrincipal(new BigDecimal("12000"));
        loan.setInterestRate(new BigDecimal("10")); // 10% annual
        loan.setStartDate(LocalDate.of(2026, 1, 1));
        
        List<RepaymentSchedule> schedules = generator.generateFlatEMI(loan, 12);
        
        assertThat(schedules).hasSize(12);
        assertThat(schedules.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(schedules.get(0).getPrincipalAmount()).isEqualByComparingTo("1000");
        assertThat(schedules.get(0).getInterestAmount()).isEqualByComparingTo("100"); // 12000 * 10% / 12
    }
}
```
*Command to run:* `./mvnw test -Dtest=RepaymentScheduleGeneratorTest` (Expected: FAIL)

**3. Implementation (Step 3):**
```java
// backend/src/main/java/com/ddicg/erp/loan/service/RepaymentScheduleGenerator.java
package com.ddicg.erp.loan.service;

import com.ddicg.erp.loan.entity.Loan;
import com.ddicg.erp.loan.entity.RepaymentSchedule;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RepaymentScheduleGenerator {
    public List<RepaymentSchedule> generateFlatEMI(Loan loan, int months) {
        List<RepaymentSchedule> schedules = new ArrayList<>();
        BigDecimal principalPerMonth = loan.getPrincipal().divide(new BigDecimal(months), 4, RoundingMode.HALF_UP);
        BigDecimal annualInterest = loan.getInterestRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal interestPerMonth = loan.getPrincipal().multiply(annualInterest).divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP);

        LocalDate current = loan.getStartDate();
        for (int i = 1; i <= months; i++) {
            current = current.plusMonths(1);
            RepaymentSchedule schedule = new RepaymentSchedule();
            schedule.setLoan(loan);
            schedule.setDueDate(current);
            schedule.setPrincipalAmount(principalPerMonth);
            schedule.setInterestAmount(interestPerMonth);
            schedule.setPenaltyAmount(BigDecimal.ZERO);
            schedule.setStatus("PENDING");
            schedules.add(schedule);
        }
        return schedules;
    }
}
```
```java
// backend/src/main/java/com/ddicg/erp/loan/service/LoanService.java
package com.ddicg.erp.loan.service;

import com.ddicg.erp.loan.entity.Loan;
import com.ddicg.erp.loan.entity.RepaymentSchedule;
import com.ddicg.erp.loan.repository.LoanRepository;
import com.ddicg.erp.loan.repository.RepaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanService {
    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository scheduleRepository;
    private final RepaymentScheduleGenerator scheduleGenerator;

    @Transactional
    public Loan approveAndGenerateSchedules(Long loanId, int months) {
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found"));
        
        if (!"PENDING".equals(loan.getStatus())) {
            throw new IllegalStateException("Only PENDING loans can be approved");
        }
        
        loan.setStatus("ACTIVE");
        List<RepaymentSchedule> schedules = scheduleGenerator.generateFlatEMI(loan, months);
        scheduleRepository.saveAll(schedules);
        loanRepository.save(loan);
        
        return loan;
    }
}
```
*Command to run:* `./mvnw test -Dtest=RepaymentScheduleGeneratorTest` (Expected: PASS)

### Task 2: Scheduled Penalty Trigger (COB Job)
- Scope: Small (Solo)
- Target Service: `backend/`
- Worktree: `.worktrees/task-2`
- Branch: `task-2`

#### Role: Worker-2
**Directive:** Execute the following TDD lifecycle strictly inside `.worktrees/task-2`.

**1. Target Files:**
- Create: `backend/src/main/java/com/ddicg/erp/loan/job/PenaltyCalculationJob.java`

**2. Failing Test (Step 1):**
```java
// backend/src/test/java/com/ddicg/erp/loan/job/PenaltyCalculationJobTest.java
package com.ddicg.erp.loan.job;

import com.ddicg.erp.loan.entity.RepaymentSchedule;
import com.ddicg.erp.loan.repository.RepaymentScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PenaltyCalculationJobTest {
    @Mock private RepaymentScheduleRepository repository;
    @InjectMocks private PenaltyCalculationJob job;

    @Test
    void shouldApplyPenaltyToOverdueSchedules() {
        RepaymentSchedule overdue = new RepaymentSchedule();
        overdue.setPrincipalAmount(new BigDecimal("1000"));
        overdue.setDueDate(LocalDate.now().minusDays(5));
        overdue.setPenaltyAmount(BigDecimal.ZERO);
        overdue.setStatus("PENDING");
        
        when(repository.findByStatusAndDueDateBefore(eq("PENDING"), any())).thenReturn(List.of(overdue));
        
        job.calculateDailyPenalties();
        
        verify(repository, times(1)).saveAll(any());
        assertThat(overdue.getPenaltyAmount()).isEqualByComparingTo("10.00");
    }
}
```
*Command to run:* `./mvnw test -Dtest=PenaltyCalculationJobTest` (Expected: FAIL)

**3. Implementation (Step 3):**
```java
// backend/src/main/java/com/ddicg/erp/loan/job/PenaltyCalculationJob.java
package com.ddicg.erp.loan.job;

import com.ddicg.erp.loan.entity.RepaymentSchedule;
import com.ddicg.erp.loan.repository.RepaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PenaltyCalculationJob {
    private final RepaymentScheduleRepository scheduleRepository;

    @Scheduled(cron = "0 0 1 * * ?") // Runs 1 AM daily
    public void calculateDailyPenalties() {
        List<RepaymentSchedule> overdueSchedules = scheduleRepository.findByStatusAndDueDateBefore("PENDING", LocalDate.now());
        for (RepaymentSchedule schedule : overdueSchedules) {
            BigDecimal penalty = schedule.getPrincipalAmount().multiply(new BigDecimal("0.01"));
            schedule.setPenaltyAmount(schedule.getPenaltyAmount().add(penalty));
        }
        scheduleRepository.saveAll(overdueSchedules);
    }
}
```
*Command to run:* `./mvnw test -Dtest=PenaltyCalculationJobTest` (Expected: PASS)

---

## Final Phase: Role: Builder
**Directive:** Multi-Service Integration Sign-Off

### Service: Backend (Spring Boot)
- Root Directory: `backend/`
- Build Command: `./mvnw clean package -DskipTests`
- Migration Check: Ensure Flyway migration `V22__create_loan_and_cashflow_tables.sql` applies cleanly on startup.
- Boot Command: `./mvnw spring-boot:run`
- Health Check: `curl -f http://localhost:8080/actuator/health` -> HTTP 200 OK