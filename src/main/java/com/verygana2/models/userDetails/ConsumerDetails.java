package com.verygana2.models.userDetails;

import java.util.List;

import com.verygana2.models.Category;
import com.verygana2.models.products.Product;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = false)
public class ConsumerDetails extends UserDetails {
    @NotBlank(message = "the name cannot be empty")
    @Size(max = 50)
    private String name;
    @NotBlank(message = "the name cannot be empty")
    @Size(max = 50)
    private String lastName;
    @Min(value = 0, message = "adsWatched must be zero or positive")
    private Integer adsWatched;

    @Min(value = 0, message = "totalWithdraws must be zero or positive")
    private Integer totalWithdraws;

    @Min(value = 0, message = "dailyAdCount must be zero or positive")
    @Max(value = 100, message = "dailyAdCount cannot exceed 100")
    private Integer dailyAdCount;

    @Size(max = 20, message = "Referral code must be at most 20 characters")
    private String referralCode;
    @NotBlank(message = "Department is required")
    @Size(max = 50)
    private String department;
    @NotBlank(message = "Municipality is required")
    @Size(max = 50)
    private String municipio;
    
    @ManyToMany
    @JoinTable(name = "consumer_preferences", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    @NotNull(message = "Preferences are required")
    @Size(min = 1, message = "At least one category must be selected")
    private List<Category> categories;

    @ManyToMany
    @JoinTable(name = "consumer_favorites_products", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
    private List<Product> favoriteProducts;
}
