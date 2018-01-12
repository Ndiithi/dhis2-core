package org.hisp.dhis.textpattern;

import com.google.common.collect.ImmutableMap;
import org.hisp.dhis.reservedvalue.ReservedValueService;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultTextPatternService
    implements TextPatternService
{

    @Autowired
    private ReservedValueService reservedValueService;

    @Override
    public String resolvePattern( TextPattern pattern, Map<String, String> values )
        throws Exception
    {
        StringBuilder resolvedPattern = new StringBuilder();
        List<TextPatternSegment> requiresGeneration = new ArrayList<>();

        for ( TextPatternSegment segment : pattern.getSegments() )
        {
            if ( isRequired( segment ) )
            {
                resolvedPattern.append( handleRequiredValue( segment, getSegmentValue( segment, values ) ) );
            }
            else if ( isOptional( segment ) )
            {
                if ( isGenerated( segment ) )
                {
                    requiresGeneration.add( segment );
                }

                resolvedPattern.append( handleOptionalValue( segment, getSegmentValue( segment, values ) ) );
            }
            else
            {
                resolvedPattern.append( handleFixedValues( segment ) );
            }
        }

        String result = resolvedPattern.toString();

        // Generate values
        for ( TextPatternSegment segment : requiresGeneration )
        {
            result = result.replaceAll( segment.getParameter(), reservedValueService
                .generateAndReserveSequentialValues( pattern.getOwnerUID(), resolvedPattern.toString(),
                    segment.getParameter(), 1 ).get( 0 ) );
        }

        return result;
    }

    @Override
    public Map<String, List<String>> getRequiredValues( TextPattern pattern )
    {
        return ImmutableMap.<String, List<String>>builder()
            .put( "REQUIRED", pattern.getSegments()
                .stream()
                .filter( this::isRequired )
                .map( TextPatternSegment::getRawSegment )
                .collect( Collectors.toList() ) )
            .put( "OPTIONAL", pattern.getSegments()
                .stream()
                .filter( this::isOptional )
                .map( TextPatternSegment::getRawSegment )
                .collect( Collectors.toList() ) )
            .build();
    }

    @Override
    public boolean validate( TextPattern textPattern, String text )
    {
        // TODO
        return TextPatternValidationUtils.validateTextPatternValue( textPattern, text);
    }

    @Override
    public TextPattern getTextPattern( TrackedEntityAttribute attribute )
        throws TextPatternParser.TextPatternParsingException
    {
        if ( attribute.getTextPattern() == null && attribute.isGenerated() )
        {
            attribute.setTextPattern( TextPatternParser.parse( attribute.getPattern() ) );
            attribute.getTextPattern().setOwnerUID( attribute.getUid() );
        }

        return attribute.getTextPattern();
    }

    private String handleFixedValues( TextPatternSegment segment )
    {
        if ( TextPatternMethod.CURRENT_DATE.getType().validatePattern( segment.getRawSegment() ) )
        {
            return new SimpleDateFormat( segment.getParameter() ).format( new Date() );
        }
        else
        {
            return segment.getParameter();
        }
    }

    private String handleOptionalValue( TextPatternSegment segment, String value )
        throws Exception
    {
        if ( value != null && !TextPatternValidationUtils.validateSegmentValue( segment, value ) )
        {
            throw new Exception( "Supplied optional value is invalid" );
        }
        else if ( value != null )
        {
            return getFormattedValue( segment, value );
        }
        else
        {
            if ( isGenerated( segment ) )
            {
                // Put parameter as placeholder, this will be handled after the rest have been resolved
                return segment.getParameter();
            }
            else
            {
                throw new Exception( "Trying to generate unknown segment: '" + segment.getMethod().name() + "'" );
            }
        }
    }

    private String handleRequiredValue( TextPatternSegment segment, String value )
        throws Exception
    {
        if ( value == null )
        {
            throw new Exception( "Missing required value" );
        }

        String res = getFormattedValue( segment, value );

        if ( res == null || !TextPatternValidationUtils.validateSegmentValue( segment, res ) )
        {
            throw new Exception( "Value is invalid: " + segment.getRawSegment() + " -> " + value );
        }

        return res;
    }

    private String getFormattedValue( TextPatternSegment segment, String value )
    {
        MethodType methodType = segment.getMethod().getType();

        return methodType.getFormattedText( segment.getParameter(), value );
    }

    private String getSegmentValue( TextPatternSegment segment, Map<String, String> values )
    {
        return values.get( segment.getRawSegment() );
    }

    private boolean isRequired( TextPatternSegment segment )
    {
        return segment.getMethod().isRequired();
    }

    private boolean isOptional( TextPatternSegment segment )
    {
        return segment.getMethod().isOptional();
    }

    private boolean isGenerated( TextPatternSegment segment )
    {
        return segment.getMethod().isGenerated();
    }
}