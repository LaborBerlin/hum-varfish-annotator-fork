# Varhab - Variant annotation for import into Varfish

## Example

The following will create `varhab.h2.db` and fill it.

```
# java -jar varhab-cli.jar \
    init-db \
        --db-path varhab \
        --exac-path path/to/ExAC.r0.3.1.sites.vep.vcf.gz \
        --ref-path path/to/hs37d5/hs37d5.fa \
        --clinvar-path path/to/clinvar_allele_trait_pairs_example_750_rows.single.b37.tsv \
        --clinvar-path path/to/clinvar_allele_trait_pairs_example_750_rows.multi.b37.tsv
```

## Formatting Source Code

```
# mvn com.coveo:fmt-maven-plugin:format -Dverbose=true
```
