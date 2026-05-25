package ${packageName};
${sourceImportStatement}
${targetImportStatement}
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper
public interface ${className} {
${mapMethods}}
